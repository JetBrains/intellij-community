package com.intellij.compiler.impl.javaCompiler;

/**
 * TODO: experimental code
 * This must be used with JDK 1.6 or higher
 * @author Eugene Zhuravlev
 *         Date: Aug 21, 2009
 */
public class AnnotationProcessorRunner {

  /*
  public static void main(String[] args) {
    final String classpath =
      "C:\\tmp\\PersistenceSample\\lib\\antlr.jar;C:\\tmp\\PersistenceSample\\lib\\asm.jar;C:\\tmp\\PersistenceSample\\lib\\cglib.jar;C:\\tmp\\PersistenceSample\\lib\\dom4j.jar;C:\\tmp\\PersistenceSample\\lib\\hibernate.jar;C:\\tmp\\PersistenceSample\\lib\\hibernate-annotations.jar;C:\\tmp\\PersistenceSample\\lib\\javaee_6.jar;C:\\tmp\\PersistenceSample\\lib\\openjpa-all-2.0.0-SNAPSHOT.jar";

    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final List<String> options = new ArrayList<String>();
    options.add("-Xprefer:source");
    options.add("-implicit:none");
    options.add("-proc:only");
    options.add("-sourcepath");
    options.add("\"\"");
    options.add("-cp");
    options.add(classpath);
    //options.add("-processor");
    //options.add("org.apache.openjpa.persistence.meta.AnnotationProcessor6");
    options.addAll(Arrays.asList(args));
    
    final DiagnosticListener<JavaFileObject> listener = new DiagnosticListener<JavaFileObject>() {
      public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
        System.out.println(diagnostic);
      }
    };
    final List<SourceFile> sources = Arrays.asList(new SourceFile[] {
      new SourceFile(new File("C:\\tmp\\PersistenceSample\\Queries2\\src\\com\\sample\\Product.java")),
      new SourceFile(new File("C:\\tmp\\PersistenceSample\\Queries2\\src\\com\\sample\\Software.java")),
      new SourceFile(new File("C:\\tmp\\PersistenceSample\\Queries2\\src\\com\\sample\\Supplier.java")),
    });
    final JavaCompiler.CompilationTask task = compiler.getTask(null, null, listener, options, null  */
/*Collections.singleton("org.apache.openjpa.persistence.meta.AnnotationProcessor6")*/  /*
, sources);

    final ProcessorManager processorManager = new ProcessorManager();
    task.setProcessors(processorManager.lookupProcessor(Collections.singleton("org.apache.openjpa.persistence.meta.AnnotationProcessor6"), classpath));
    final Boolean rv = task.call();

    final ProcessingEnvironment env = processorManager.getEnvironment();
    System.exit(rv.booleanValue()? 0 : -1);
  }


  private static class ProcessorManager {
    ProcessingEnvironment myEnvironment = null;

    public ProcessingEnvironment getEnvironment() {
      return myEnvironment;
    }

    public Collection<Processor> lookupProcessor(Collection<String> names, String classpath) {
      StringTokenizer tokenizer = new StringTokenizer(classpath, File.pathSeparator, false);
      final List<URL> urls = new ArrayList<URL>();
      while (tokenizer.hasMoreTokens()) {
        final String path = tokenizer.nextToken();
        try {
          urls.add(new File(path).toURL());
        }
        catch (MalformedURLException e) {
          e.printStackTrace();
        }
      }
      final URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]));
      List<Processor> processors = new ArrayList<Processor>();
      for (String qName : names) {
        try {
          final Class<?> processorClass = loader.loadClass(qName);
          processors.add(wrapProcessor((Processor)processorClass.newInstance()));
        }
        catch (ClassNotFoundException e) {
          e.printStackTrace();
        }
        catch (InstantiationException e) {
          e.printStackTrace();
        }
        catch (IllegalAccessException e) {
          e.printStackTrace();
        }
      }
      return processors;
    }

    private Processor wrapProcessor(final Processor original) {
      return (Processor)Proxy.newProxyInstance(original.getClass().getClassLoader(), new Class[]{Processor.class}, new InvocationHandler() {
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          if ("init".equals(method.getName())) {
            if (myEnvironment == null) {
              myEnvironment = wrapEnvironment((ProcessingEnvironment)args[0]);
            }
            return method.invoke(original, myEnvironment);
          }
          return method.invoke(original, args);
        }
      });
    }

    private ProcessingEnvironment wrapEnvironment(final ProcessingEnvironment original) {
      final Filer originalFiler = original.getFiler();
      final Filer filer = (Filer)Proxy.newProxyInstance(originalFiler.getClass().getClassLoader(), new Class<?>[] {Filer.class}, new InvocationHandler() {
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          return method.invoke(original, args);
        }
      });
      
      return (ProcessingEnvironment)Proxy.newProxyInstance(original.getClass().getClassLoader(), new Class[]{ProcessingEnvironment.class}, new InvocationHandler() {
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
          if ("getFiler".equals(method.getName())) {
            return filer;
          }
          System.out.println("Environment::"+method.getName());
          return method.invoke(original, args);
        }
      });
    }

  }

  private static class SourceFile extends SimpleJavaFileObject {
    private final File myFile;

    private SourceFile(final File file) {
      super(file.toURI(), Kind.SOURCE);
      myFile = file;
    }

    public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
      return new CharArrayCharSequence(FileUtil.loadFileText(myFile));
    }
  }
  */

}
