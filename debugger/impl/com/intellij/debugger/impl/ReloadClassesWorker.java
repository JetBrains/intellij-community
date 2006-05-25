package com.intellij.debugger.impl;

import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.MethodSignature;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.MessageCategory;
import com.sun.jdi.Method;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VMDisconnectedException;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Mar 10, 2004
 * Time: 7:12:57 PM
 * To change this template use File | Settings | File Templates.
 */
class ReloadClassesWorker {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.ReloadClassesWorker");

  private final DebuggerSession  myDebuggerSession;
  private final HotSwapProgress  myProgress;

  public ReloadClassesWorker(DebuggerSession session, HotSwapProgress progress) {
    myDebuggerSession = session;
    myProgress = progress;
  }

  private DebugProcessImpl getDebugProcess() {
    return myDebuggerSession.getProcess();
  }

  private Project getProject() {
    return myDebuggerSession.getProject();
  }

  private void reportObsoleteFrames(final Map<ThreadReferenceProxyImpl, PsiMethod[]> methodsOnTheStack) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          VirtualMachineProxyImpl vm = getDebugProcess().getVirtualMachineProxy();
          for (Iterator it = vm.allThreads().iterator(); it.hasNext();) {
            try {
              ThreadReferenceProxyImpl threadProxy = (ThreadReferenceProxyImpl)it.next();
              if (threadProxy.isSuspended()) {
                PsiMethod[] methods = methodsOnTheStack.get(threadProxy);
                if (methods != null) {
                  for (Iterator itf = threadProxy.frames().iterator(); itf.hasNext();) {
                    StackFrameProxyImpl stackFrame = (StackFrameProxyImpl)itf.next();
                    if (stackFrame.location().method().isObsolete()) {
                      PsiMethod method = methods[stackFrame.getFrameIndex()];

                      if(method != null) {
                        PsiFile psiFile = method.getContainingFile();
                        VirtualFile file = null;
                        int lineIndex = 0;

                        if (psiFile != null) {
                          file = psiFile.getVirtualFile();
                          if(file != null) {
                            lineIndex = StringUtil.offsetToLineNumber(psiFile.getText(), method.getTextOffset());
                          }

                          MethodSignature sig = method.getSignature(PsiSubstitutor.EMPTY);
                          myProgress.addMessage(
                            MessageCategory.WARNING,
                            getPresentableSignatureText(sig) + " : "+ DebuggerBundle.message("warning.hotswap.ignored.breakpoints")
                          );
                        }
                      }
                    }
                  }
                }
              }
            }
            catch (EvaluateException e) {
            }
            catch (VMDisconnectedException e) {
            }
          }
        }
      });
    }

  private String getPresentableSignatureText(final MethodSignature sig) {
    StringBuffer buf = new StringBuffer();
    final PsiTypeParameter[] typeParameters = sig.getTypeParameters();
    if (typeParameters.length != 0) {
      String sep = "<";
      for (PsiTypeParameter typeParameter : typeParameters) {
        buf.append(sep).append(typeParameter.getName());
        sep = ", ";
      }
      buf.append(">");
    }

    buf.append(sig.getName()).append("(");
    final PsiType[] types = sig.getParameterTypes();
    for (int i = 0; i < types.length; i++) {
      PsiType type = types[i];
      if (i > 0) {
        buf.append(", ");
      }
      buf.append(type.getPresentableText());
    }
    buf.append(')');

    return buf.toString();
  }

  private Map<ThreadReferenceProxyImpl, PsiMethod[]> getMethodsOnTheStack() {
    final Map<ThreadReferenceProxyImpl, PsiMethod[]> myThreadsToMethods = new HashMap<ThreadReferenceProxyImpl, PsiMethod[]>();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        VirtualMachineProxyImpl vm = getDebugProcess().getVirtualMachineProxy();
        try {
          for (Iterator it = vm.allThreads().iterator(); it.hasNext();) {
            ThreadReferenceProxyImpl threadProxy = (ThreadReferenceProxyImpl)it.next();
            if (threadProxy.isSuspended()) {
              List frames = threadProxy.frames();

              PsiMethod[] methods = new PsiMethod[frames.size()];
              myThreadsToMethods.put(threadProxy, methods);
              for (Iterator itf = frames.iterator(); itf.hasNext();) {
                StackFrameProxyImpl stackFrame = (StackFrameProxyImpl)itf.next();
                methods[stackFrame.getFrameIndex()] = findPsiMethod(getProject(), stackFrame);
              }
            }
          }
        }
        catch (EvaluateException e) {
        }
      }
    });

    return myThreadsToMethods;
  }

  private PsiMethod findPsiMethod(Project project, StackFrameProxyImpl stackFrame) {
    try {
      String className = DebuggerUtilsEx.signatureToName(stackFrame.location().declaringType().signature());
      GlobalSearchScope scope = GlobalSearchScope.allScope(project);
      PsiClass cls = PsiManager.getInstance(project).findClass(className, scope);
      if(cls == null) return null;

      Method method = stackFrame.location().method();
      PsiMethod[] methods = cls.findMethodsByName(method.name(), false);
      nextMethod: for (int i = 0; i < methods.length; i++) {
        PsiMethod m = methods[i];
        if (method.isStatic() == m.hasModifierProperty(PsiModifier.STATIC)) {
          PsiParameter[] params = m.getParameterList().getParameters();
          List argTypes = method.argumentTypeNames();
          if (argTypes.size() == params.length) {
            int j = 0;
            for (Iterator iterator = argTypes.iterator(); iterator.hasNext(); j++) {
              String typeName = (String)iterator.next();
              if (!params[j].getType().getCanonicalText().equals(typeName)) continue nextMethod;
            }
            return m;
          }
        }
      }
    }
    catch (EvaluateException e) {
      e.printStackTrace();
    }

    return null;
  }

  private void processException(Throwable e) {
    if (e.getMessage() != null) {
      myProgress.addMessage(MessageCategory.ERROR, e.getMessage());
    }

    if (e instanceof ProcessCanceledException) {
      myProgress.addMessage(MessageCategory.INFORMATION, DebuggerBundle.message("error.operation.canceled"));
      return;
    }

    if (e instanceof UnsupportedOperationException) {
      myProgress.addMessage(MessageCategory.ERROR, DebuggerBundle.message("error.operation.not.supported.by.vm"));
    }
    else if (e instanceof NoClassDefFoundError) {
      myProgress.addMessage(MessageCategory.ERROR, DebuggerBundle.message("error.class.def.not.found", e.getLocalizedMessage()));
    }
    else if (e instanceof VerifyError) {
      myProgress.addMessage(MessageCategory.ERROR, DebuggerBundle.message("error.verification.error", e.getLocalizedMessage()));
    }
    else if (e instanceof UnsupportedClassVersionError) {
      myProgress.addMessage(MessageCategory.ERROR, DebuggerBundle.message("error.unsupported.class.version", e.getLocalizedMessage()));
    }
    else if (e instanceof ClassFormatError) {
      myProgress.addMessage(MessageCategory.ERROR, DebuggerBundle.message("error.class.format.error", e.getLocalizedMessage()));
    }
    else if (e instanceof ClassCircularityError) {
      myProgress.addMessage(MessageCategory.ERROR, DebuggerBundle.message("error.class.circularity.error", e.getLocalizedMessage()));
    }
    else {
      myProgress.addMessage(
        MessageCategory.ERROR,
        DebuggerBundle.message("error.exception.while.reloading", e.getClass().getName(), e.getLocalizedMessage())
      );
    }
  }

  private byte[] loadFile(VirtualFile file) {
    try {
      return file.contentsToByteArray();
    }
    catch (IOException e) {
      return null;
    }
  }

  public void reloadClasses(final HashMap<String, HotSwapFile> modifiedClasses) {
    if(modifiedClasses == null || modifiedClasses.size() == 0) {
      myProgress.addMessage(MessageCategory.INFORMATION, DebuggerBundle.message("status.hotswap.loaded.classes.up.to.date"));
      return;
    }

    VirtualMachineProxyImpl virtualMachineProxy = getDebugProcess().getVirtualMachineProxy();
    if(virtualMachineProxy == null) return;

    virtualMachineProxy.suspend();

    final Project project = getDebugProcess().getProject();
    try {
      Map<ThreadReferenceProxyImpl, PsiMethod[]> methodsOnTheStack = getMethodsOnTheStack();

      Map redefineMap = new HashMap();
      int classN = 0;
      for (Iterator iterator = modifiedClasses.keySet().iterator(); iterator.hasNext();) {
        classN++;
        String qualifiedName = (String)iterator.next();
        if (qualifiedName != null) {
          myProgress.setText(qualifiedName);
          myProgress.setFraction(classN / (double)modifiedClasses.size());
        }

        final HotSwapFile fileDescr = modifiedClasses.get(qualifiedName);

        //[max]: Generic enabled Computable<byte[]> confuses degenerator.
        byte[] buffer = (byte[])ApplicationManager.getApplication().runReadAction(new Computable() {
          public Object compute() {
            return loadFile(fileDescr.file);
          }
        });

        redefineMap.clear();
        List classes = virtualMachineProxy.classesByName(qualifiedName);
        for (Iterator i = classes.iterator(); i.hasNext();) {
          ReferenceType reference = (ReferenceType)i.next();

          if (buffer == null) {
            myProgress.addMessage(MessageCategory.ERROR, DebuggerBundle.message("error.io.error"));
          }
          redefineMap.put(reference, buffer);
        }
        getDebugProcess().getVirtualMachineProxy().redefineClasses(redefineMap);
        //myProgress.addMessage(MessageCategory.INFORMATION, new String[] { qualifiedName + " reloaded" }, null, -1, -1);
      }
      myProgress.setFraction(1);
      myProgress.addMessage(MessageCategory.INFORMATION, DebuggerBundle.message("status.classes.reloaded", modifiedClasses.size()));
      if (LOG.isDebugEnabled()) {
        LOG.debug("classes reloaded");
      }
      reportObsoleteFrames(methodsOnTheStack);
      if (LOG.isDebugEnabled()) {
        LOG.debug("obsolete frames reported");
      }
    }
    catch (Throwable e) {
      processException(e);
    }

    DebuggerInvocationUtil.invokeLater(getProject(), new Runnable() {
      public void run() {
        final BreakpointManager breakpointManager = (DebuggerManagerEx.getInstanceEx(project)).getBreakpointManager();
        breakpointManager.reloadBreakpoints();
        breakpointManager.updateAllRequests();
        if (LOG.isDebugEnabled()) {
          LOG.debug("requests updated");
          LOG.debug("time stamp set");
        }
        myDebuggerSession.refresh();

        getDebugProcess().getManagerThread().invokeLater(new DebuggerCommandImpl() {
          protected void action() throws Exception {
            try {
              getDebugProcess().getVirtualMachineProxy().resume();
            }
            catch (Exception e) {
              processException(e);
            }
          }
        });
      }
    });
  }
}
