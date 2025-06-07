// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.util.Iterators;
import org.jetbrains.jps.util.Iterators.Function;
import org.jetbrains.jps.util.Pair;
import org.jetbrains.jps.util.Ref;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.*;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;

public final class APIWrappers {

  /**
   * WARNING: this method may be called from annotation processor's code via reflection. The signature change or rename may break compatibility
   *
   * @param iface the interface the unwrapped object is expected to implement
   * @param wrapper a possibly wrapped object to be unwrapped
   * @return the object, the passed wrapper delegates to, if the wrapper object was wrapped with the help of {@link APIWrappers#wrap(Class, Object, Class, Object)}, null otherwise
   */
  @Nullable
  public static <T> T unwrap(Class<? extends T> iface, T wrapper) {
    if (wrapper instanceof WrapperDelegateAccessor) {
      final Object delegate = ((WrapperDelegateAccessor<?>)wrapper).getWrapperDelegate();
      if (iface.isInstance(delegate)) {
        return iface.cast(delegate);
      }
    }
    return null;
  }

  @ApiStatus.Internal
  public static <T extends FileObject> DiagnosticOutputConsumer newDiagnosticListenerWrapper(ProcessingContext procContext, final DiagnosticOutputConsumer delegate) {
    return wrap(DiagnosticOutputConsumer.class, new DiagnosticListenerWrapper<T>(procContext, delegate));
  }

  @ApiStatus.Internal
  public static final class ProcessingContext {
    private final JpsJavacFileManager myFileManager;
    private Iterable<Processor> myAllProcessors = Collections.emptyList();
    private final Map<ProcessingEnvironment, ProcessingEnvironment> myWrappers = new HashMap<>(); // procEnv -> wrappedProcEnv
    private String myLastProcName;
    private final Map<String, String> myProcNamesMap = new HashMap<>(); // real proc className -> wrapped proc className
    
    public ProcessingContext(@NotNull JpsJavacFileManager fileManager) {
      myFileManager = fileManager;
    }

    @NotNull
    public JpsJavacFileManager getFileManager() {
      return myFileManager;
    }

    @NotNull
    public ProcessingEnvironment getWrappedProcessingEnvironment(ProcessingEnvironment processingEnv) {
      ProcessingEnvironment wrapped = myWrappers.get(processingEnv);
      if (wrapped == null) {
        myWrappers.put(processingEnv, wrapped = wrap(ProcessingEnvironment.class, new ProcessingEnvironmentWrapper(processingEnv, myFileManager)));
      }
      return wrapped;
    }

    public String getProcessorName(Processor proc) {
      return (proc instanceof WrapperDelegateAccessor<?>? ((WrapperDelegateAccessor<?>)proc).getWrapperDelegate() : proc).getClass().getName();
    }

    void setLastExecutedProcessorName(Processor proc) {
      myLastProcName = getProcessorName(proc);
    }

    Iterable<Processor> wrapProcessors(Iterable<? extends Processor> processors) {
      return myAllProcessors = Iterators.map(processors, new Function<Processor, Processor>() {
        @Override
        public Processor fun(Processor processor) {
          return wrap(Processor.class, new ProcessorWrapper(processor, ProcessingContext.this));
        }
      });
    }

    @Nullable
    public String adjustMessage(String message) {
      if (message != null) {
        try {
          final String realProcName = myLastProcName;
          if (realProcName != null && !realProcName.isEmpty()) {
            final String wrappedName = lookupWrappedProcName(realProcName);
            if (wrappedName != null) {
              return message.replace(wrappedName, realProcName);
            }
          }
        }
        catch (Throwable ignored) {
           //iterating namesMap may cause unexpected AP class loading exceptions
        }
      }
      return message;
    }

    private String lookupWrappedProcName(String procName) {
      String wrappedName = myProcNamesMap.get(procName);
      if (wrappedName == null) {
        for (Processor proc : myAllProcessors) {
          if (proc instanceof WrapperDelegateAccessor<?>) {
            myProcNamesMap.put(getProcessorName(proc), proc.getClass().getName());
          }
        }
        wrappedName = myProcNamesMap.get(procName);
      }
      return wrappedName;
    }
  }

  @ApiStatus.Internal
  public interface WrapperDelegateAccessor<T> {
    T getWrapperDelegate();
  }

  @ApiStatus.Internal
  public abstract static class DynamicWrapper<T> implements WrapperDelegateAccessor<T> {

    private final T myDelegate;

    DynamicWrapper(T delegate) {
      myDelegate = delegate;
    }

    @Override
    public final T getWrapperDelegate() {
      return myDelegate;
    }
  }

  @SuppressWarnings("unchecked")
  @ApiStatus.Internal
  public final static class DiagnosticListenerWrapper<T extends FileObject> extends DynamicWrapper<DiagnosticOutputConsumer> implements DiagnosticListener<T>{
    private final ProcessingContext myProcContext;

    DiagnosticListenerWrapper(ProcessingContext procContext, DiagnosticOutputConsumer delegate) {
      super(delegate);
      myProcContext = procContext;
    }

    public void outputLineAvailable(String line) {
      getWrapperDelegate().outputLineAvailable(myProcContext.adjustMessage(line));
    }

    @Override
    public void report(Diagnostic<? extends T> diagnostic) {
      getWrapperDelegate().report(wrap(Diagnostic.class, new DiagnosticWrapper<>(myProcContext, (Diagnostic<T>)diagnostic)));
    }
  }

  @ApiStatus.Internal
  public static final class DiagnosticWrapper<T> extends DynamicWrapper<Diagnostic<T>> {
    private final ProcessingContext myProcContext;

    DiagnosticWrapper(ProcessingContext procContext, Diagnostic<T> delegate) {
      super(delegate);
      myProcContext = procContext;
    }

    public String getMessage(Locale locale) {
      try {
        try {
          return myProcContext.adjustMessage(getWrapperDelegate().getMessage(locale));
        }
        catch (Throwable e) {
          // Diagnostic.getMessage() can cause unexpected exceptions while building the message based on a structured data contained in the disgnostic object
          // For example, it may fail with a class name resolution error, if some symbols required to build a message are not resolvable at the moment
          // Sometimes just repeating a call helps to get the actual diagnostic message
          return myProcContext.adjustMessage(getWrapperDelegate().getMessage(locale));
        }
      }
      catch (Throwable e) {
        // fallback logic
        return "Unexpected error: " + e.getClass() + ": " + e.getMessage();
      }
    }
  }

  @ApiStatus.Internal
  public static final class ProcessorWrapper extends DynamicWrapper<Processor> {
    private final ProcessingContext myProcessingContext;
    private boolean myCodeShown = false;
    private ProcessingEnvironment myProcessingEnv;

    ProcessorWrapper(Processor delegate, ProcessingContext context) {
      super(delegate);
      myProcessingContext = context;
    }

    public void init(ProcessingEnvironment processingEnv) {
      myProcessingEnv = processingEnv;
      final Ref<ClassLoader> oldCtxLoader = setupContextClassLoader();
      try {
        getWrapperDelegate().init(myProcessingContext.getWrappedProcessingEnvironment(processingEnv));
      }
      catch (IllegalArgumentException e) {
        sendDiagnosticWarning(processingEnv, e);
        throw e;
      }
      finally {
        restoreContextClassLoader(oldCtxLoader);
      }
    }

    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
      final Ref<ClassLoader> oldCtxLoader = setupContextClassLoader();
      try {
        final Processor delegate = getWrapperDelegate();
        myProcessingContext.setLastExecutedProcessorName(delegate);
        return delegate.process(annotations, roundEnv);
      }
      catch (IllegalArgumentException e) {
        sendDiagnosticWarning(myProcessingEnv, e);
        throw e;
      }
      finally {
        restoreContextClassLoader(oldCtxLoader);
      }
    }

    /*
      Some processors may use libraries/frameworks requiring sophisticated setup via thread context class loader.
      This ensures that the context loader is the same as the one used to load processor itself:
      it might help to avoid possible conflicts with JPS core classes
     */
    @Nullable
    private Ref<ClassLoader> setupContextClassLoader() {
      final Processor delegate = getWrapperDelegate();
      if (delegate != null) {
        final Thread currentThread = Thread.currentThread();
        final ClassLoader processorLoader = delegate.getClass().getClassLoader();
        final ClassLoader currentCtxLoader = currentThread.getContextClassLoader();
        if (processorLoader != currentCtxLoader) {
          currentThread.setContextClassLoader(processorLoader);
          return Ref.create(currentCtxLoader);
        }
      }
      return null;
    }

    @SuppressWarnings("MethodMayBeStatic")
    private void restoreContextClassLoader(@Nullable Ref<ClassLoader> loaderRef) {
      if (loaderRef != null) {
        Thread.currentThread().setContextClassLoader(loaderRef.get());
      }
    }

    private void sendDiagnosticWarning(ProcessingEnvironment processingEnv, Throwable e) {
      if (processingEnv != null && !myCodeShown) {
        processingEnv.getMessager().printMessage(
          Diagnostic.Kind.MANDATORY_WARNING,
          "The " + e.getClass() + " may be caused by the wrapped ProcessingEnvironment object.\n" +
          "Please pass the wrapped ProcessingEnvironment further to super.init().\n"+
          "If you need to access the original ProcessingEnvironment object (e.g. for creating com.sun.source.util.Trees.instance(ProcessingEnvironment)), you may use following code in the processor implementation:\n\n" +
          getUnwrapCodeSuggestion(ProcessingEnvironment.class, "processingEnv")
        );
        processingEnv.getMessager().printMessage(
          Diagnostic.Kind.MANDATORY_WARNING,
          "Workaround: to make project compile with the current annotation processor implementation, start JPS with VM option: -D"+JavacMain.TRACK_AP_GENERATED_DEPENDENCIES_PROPERTY + "=false\n" +
          "When run from IDE, the option can be set in \"Compiler Settings | build process VM options\""
        );
        myCodeShown = true;
      }
    }
  }

  @ApiStatus.Internal
  public final static class ProcessingEnvironmentWrapper extends DynamicWrapper<ProcessingEnvironment> {
    private final JpsJavacFileManager myFileManager;
    private Filer myFilerImpl;

    ProcessingEnvironmentWrapper(ProcessingEnvironment delegate, JpsJavacFileManager fileManager) {
      super(delegate);
      myFileManager = fileManager;
    }

    public Filer getFiler() {
      Filer impl = myFilerImpl;
      if (impl == null) {
        final Filer delegateFiler = getWrapperDelegate().getFiler();
        myFilerImpl = impl = wrap(Filer.class, new FilerWrapper(delegateFiler, myFileManager, getWrapperDelegate().getElementUtils()));
      }
      return impl;
    }
  }

  @ApiStatus.Internal
  public final static class FilerWrapper extends DynamicWrapper<Filer> implements Filer {
    private final JpsJavacFileManager myFileManager;
    private final Function<Element, String> convertToClassName;

    FilerWrapper(Filer delegate, JpsJavacFileManager fileManager, final Elements elementUtils) {
      super(delegate);
      myFileManager = fileManager;
      convertToClassName = new ClassNameFinder(elementUtils);
    }

    @Override
    public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
      addMapping(name, originatingElements != null? Arrays.asList(originatingElements) : Collections.<Element>emptyList());
      return getWrapperDelegate().createSourceFile(name, originatingElements);
    }

    @Override
    public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
      addMapping(name, originatingElements != null? Arrays.asList(originatingElements) : Collections.<Element>emptyList());
      return getWrapperDelegate().createClassFile(name, originatingElements);
    }

    @Override
    public FileObject createResource(JavaFileManager.Location location, CharSequence moduleAndPkg, CharSequence relativeName, Element... originatingElements) throws IOException {
      if (originatingElements != null && originatingElements.length > 0) {
        final String resourceName;
        if (moduleAndPkg == null) {
          resourceName = relativeName.toString();
        }
        else {
          StringBuilder buf = new StringBuilder();
          for (int i = 0, len = moduleAndPkg.length(); i < len; i++) {
            char ch = moduleAndPkg.charAt(i);
            if (ch == '/') {
              buf.setLength(0); // skip module name
            }
            else if (ch == '.') {
              buf.append('/');
            }
            else {
              buf.append(ch);
            }
          }
          if (buf.length() > 0 && buf.charAt(buf.length() - 1) != '/') {
            buf.append('/');
          }
          resourceName = buf.append(relativeName).toString();
        }
        // Format: [package-path/]relative-name
        // package-path is a package-name where '.' replaced with '/'
        addMapping(resourceName, Arrays.asList(originatingElements));
      }
      return getWrapperDelegate().createResource(location, moduleAndPkg, relativeName, originatingElements != null ? originatingElements : new Element[0]);
    }

    @Override
    public FileObject getResource(JavaFileManager.Location location, CharSequence moduleAndPkg, CharSequence relativeName) throws IOException {
      return getWrapperDelegate().getResource(location, moduleAndPkg, relativeName);
    }

    private void addMapping(CharSequence resourceName, final Collection<? extends Element> elements) {
      if (resourceName != null && resourceName.length() > 0 && !elements.isEmpty()) {
        myFileManager.addAnnotationProcessingClassMapping(resourceName.toString(), Iterators.filter(Iterators.map(elements, convertToClassName), Iterators.notNullFilter()));
      }
    }
  }

  @NotNull
  private static <T, W extends DynamicWrapper<? extends T>> T wrap(@NotNull Class<T> ifaceClass, @NotNull final W wrapper) {
    return wrap(ifaceClass, wrapper, DynamicWrapper.class, wrapper.getWrapperDelegate());
  }

  @ApiStatus.Internal
  @NotNull
  public static <T> T wrap(@NotNull Class<T> ifaceClass, @NotNull final Object wrapper, @NotNull final Class<?> parentToStopSearchAt, @NotNull final T delegateTo) {
    return ifaceClass.cast(Proxy.newProxyInstance(APIWrappers.class.getClassLoader(), new Class<?>[]{ifaceClass, WrapperDelegateAccessor.class}, new InvocationHandler() {
      private final Map<Method, Pair<Method, Object>> myCallHandlers = Collections.synchronizedMap(new HashMap<Method, Pair<Method, Object>>());
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        try {
          final Pair<Method, Object> call = getCallHandlerMethod(method);
          return call.first.invoke(call.second, args);
        }
        catch (InvocationTargetException e) {
          final Throwable cause = e.getCause();
          throw cause != null? cause : e;
        }
      }

      @NotNull
      private Pair<Method, Object> getCallHandlerMethod(Method method) {
        Pair<Method, Object> pair = myCallHandlers.get(method);
        if (pair == null) {
          if (WrapperDelegateAccessor.class.equals(method.getDeclaringClass())) {
            pair = wrapper instanceof WrapperDelegateAccessor? Pair.create(method, wrapper) : Pair.<Method, Object>create(method, new WrapperDelegateAccessor<T>() {
              @Override
              public T getWrapperDelegate() {
                return delegateTo;
              }
            });
          }
          else {
            // important: look for implemented methods starting from the actual class
            Class<?> aClass = wrapper.getClass();
            while (!(parentToStopSearchAt.equals(aClass) || Object.class.equals(aClass))) {
              try {
                pair = Pair.create(aClass.getDeclaredMethod(method.getName(), method.getParameterTypes()), wrapper);
                break;
              }
              catch (NoSuchMethodException e) {
                aClass = aClass.getSuperclass();
              }
            }
            if (pair == null) {
              pair = Pair.<Method, Object>create(method, delegateTo);
            }
          }
          myCallHandlers.put(method, pair);
        }
        return pair;
      }
    }));
  }

  private static final class ClassNameFinder implements Function<Element, String> {
    private static final Method ourGetQualifiedNameMethod;
    private final Name myEmptyName;

    static {
      Method method = null;
      try {
        method = Class.forName("javax.lang.model.element.QualifiedNameable").getMethod("getQualifiedName");
      }
      catch (Throwable ignored) {
      }
      ourGetQualifiedNameMethod = method;
    }

    ClassNameFinder(Elements elementUtils) {
      myEmptyName = elementUtils.getName("");
    }

    @Override
    public String fun(Element element) {
      Name qName = null;
      while (element != null) {
        if (element instanceof TypeElement) {
          qName = ((TypeElement)element).getQualifiedName();
        }
        else if (element instanceof PackageElement) {
          qName = ((PackageElement)element).getQualifiedName();
        }
        else if (ourGetQualifiedNameMethod != null && ourGetQualifiedNameMethod.getDeclaringClass().isAssignableFrom(element.getClass())) {
          try {
            qName = (Name)ourGetQualifiedNameMethod.invoke(element);
          }
          catch (Throwable ignored) {
          }
        }
        if (qName != null) {
          if (!qName.equals(myEmptyName)) {
            return qName.toString();
          }
          qName = null;
        }
        element = element.getEnclosingElement();
      }
      return null;
    }
  }

  private static String getUnwrapCodeSuggestion(Class<?> ifaceClass, String objVarName) {
    return ifaceClass.getSimpleName() + " unwrapped" + objVarName + " = " + "jbUnwrap(" + ifaceClass.getSimpleName() + ".class, " + objVarName + ");" +
      "\n\n\t\twhere\n\n" +
      "private static <T> T jbUnwrap(Class<? extends T> iface, T wrapper) {\n" +
      "  T unwrapped = null;\n" +
      "  try {\n" +
      "    final Class<?> apiWrappers = wrapper.getClass().getClassLoader().loadClass(\"org.jetbrains.jps.javac.APIWrappers\");\n" +
      "    final Method unwrapMethod = apiWrappers.getDeclaredMethod(\"unwrap\", Class.class, Object.class);\n" +
      "    unwrapped = iface.cast(unwrapMethod.invoke(null, iface, wrapper));\n" +
      "  }\n" +
      "  catch (Throwable ignored) {}\n" +
      "  return unwrapped != null? unwrapped : wrapper;\n" +
      "}";
  }
}
