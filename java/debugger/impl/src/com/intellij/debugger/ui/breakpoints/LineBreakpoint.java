// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class LineBreakpoint
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.ContextUtil;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.MethodBytecodeUtil;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import com.intellij.xdebugger.impl.XDebuggerUtilImpl;
import com.sun.jdi.*;
import com.sun.jdi.event.LocatableEvent;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaBreakpointProperties;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

public class LineBreakpoint<P extends JavaBreakpointProperties> extends BreakpointWithHighlighter<P> {
  private final boolean myIgnoreSameLineLocations;
  private volatile String myMethodName = null;

  static final Logger LOG = Logger.getInstance(LineBreakpoint.class);

  public static final @NonNls Key<LineBreakpoint> CATEGORY = BreakpointCategory.lookup("line_breakpoints");

  protected LineBreakpoint(Project project, XBreakpoint xBreakpoint) {
    this(project, xBreakpoint, true);
  }

  protected LineBreakpoint(Project project, XBreakpoint xBreakpoint, boolean ignoreSameLineLocations) {
    super(project, xBreakpoint);
    myIgnoreSameLineLocations = ignoreSameLineLocations;
  }

  @Override
  protected Icon getDisabledIcon(boolean isMuted) {
    if (DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().findMasterBreakpoint(this) != null) {
      return isMuted ? AllIcons.Debugger.Db_muted_dep_line_breakpoint : AllIcons.Debugger.Db_dep_line_breakpoint;
    }
    return null;
  }

  @Override
  protected Icon getVerifiedIcon(boolean isMuted) {
    return XDebuggerUtilImpl.getVerifiedIcon(myXBreakpoint);
  }

  @Override
  protected Icon getVerifiedWarningsIcon(boolean isMuted) {
    return LayeredIcon.layeredIcon(new Icon[]{isMuted ? AllIcons.Debugger.Db_muted_breakpoint : AllIcons.Debugger.Db_set_breakpoint,
                               AllIcons.General.WarningDecorator});
  }

  @Override
  public Key<LineBreakpoint> getCategory() {
    return CATEGORY;
  }

  @Override
  protected void createOrWaitPrepare(DebugProcessImpl debugProcess, String classToBeLoaded) {
    if (isInScopeOf(debugProcess, classToBeLoaded)) {
      super.createOrWaitPrepare(debugProcess, classToBeLoaded);
    }
  }

  @Override
  protected void createRequestForPreparedClass(final DebugProcessImpl debugProcess, final ReferenceType classType) {
    if (!ReadAction.compute(() -> isInScopeOf(debugProcess, classType.name()))) {
      if (LOG.isDebugEnabled()) {
        LOG.debug(classType.name() + " is out of debug-process scope, breakpoint request won't be created for line " + getLineIndex());
      }
      return;
    }
    try {
      SourcePosition position = getSourcePosition();
      List<Location> locations = debugProcess.getPositionManager().locationsOfLine(classType, position);
      if (!locations.isEmpty()) {
        VirtualMachineProxyImpl vm = debugProcess.getVirtualMachineProxy();
        locations = StreamEx.of(locations).peek(loc -> {
          if (LOG.isDebugEnabled()) {
            LOG.debug("Found location [codeIndex=" + loc.codeIndex() +
                      "] for reference type " + classType.name() +
                      " at line " + getLineIndex() +
                      "; isObsolete: " + (vm.versionHigher("1.4") && loc.method().isObsolete()));
          }
        }).filter(l -> acceptLocation(debugProcess, classType, l)).toList();

        if (getProperties() instanceof JavaLineBreakpointProperties props && props.isConditionalReturn()) {
          if (DebuggerUtils.isAndroidVM(vm.getVirtualMachine())) {
            XDebuggerManagerImpl.getNotificationGroup()
              .createNotification(JavaDebuggerBundle.message("message.conditional.return.breakpoint.on.android"), MessageType.INFO)
              .notify(debugProcess.getProject());
          }
          else if (vm.canGetBytecodes() && vm.canGetConstantPool()) {
            locations = locations.stream()
              .map(l -> l.method())
              .distinct()
              .flatMap(m -> JavaLineBreakpointType.collectInlineConditionalReturnLocations(m, position.getLine() + 1))
              .toList();
          }
        }
        else if (myIgnoreSameLineLocations) {
          locations = MethodBytecodeUtil.removeSameLineLocations(locations);
        }

        for (Location loc : locations) {
          createLocationBreakpointRequest(this, loc, debugProcess);
          if (LOG.isDebugEnabled()) {
            LOG.debug("Created breakpoint request for reference type " + classType.name() + " at line " + getLineIndex() + "; codeIndex=" + loc.codeIndex());
          }
        }
      }
      else if (DebuggerUtilsEx.allLineLocations(classType) == null) {
        // there's no line info in this class
        debugProcess.getRequestsManager()
          .setInvalid(this, JavaDebuggerBundle.message("error.invalid.breakpoint.no.line.info", classType.name()));
        if (LOG.isDebugEnabled()) {
          LOG.debug("No line number info in " + classType.name());
        }
      }
      else {
        // there's no executable code in this class
        debugProcess.getRequestsManager().setInvalid(this, JavaDebuggerBundle.message(
          "error.invalid.breakpoint.no.executable.code", (getLineIndex() + 1), classType.name())
        );
        if (LOG.isDebugEnabled()) {
          LOG.debug("No locations of type " + classType.name() + " found at line " + getLineIndex());
        }
      }
    }
    catch (ClassNotPreparedException ex) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("ClassNotPreparedException: " + ex.getMessage());
      }
      // there's a chance to add a breakpoint when the class is prepared
    }
    catch (ObjectCollectedException ex) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("ObjectCollectedException: " + ex.getMessage());
      }
      // there's a chance to add a breakpoint when the class is prepared
    }
    catch (Exception ex) {
      LOG.info(ex);
    }
    updateUI();
  }

  private static final Pattern ourAnonymousPattern = Pattern.compile(".*\\$\\d*$");

  private static boolean isAnonymousClass(ReferenceType classType) {
    if (classType instanceof ClassType) {
      return ourAnonymousPattern.matcher(classType.name()).matches();
    }
    return false;
  }

  protected boolean acceptLocation(final DebugProcessImpl debugProcess, ReferenceType classType, final Location loc) {
    // Some frameworks may create synthetic methods with lines mapped to user code, see IDEA-143852
    // if (DebuggerUtils.isSynthetic(method)) { return false; }
    if (isAnonymousClass(classType)) {
      Method method = loc.method();
      if ((method.isConstructor() && loc.codeIndex() == 0) || method.isBridge()) return false;
    }
    SourcePosition position = debugProcess.getPositionManager().getSourcePosition(loc);
    if (position == null) return false;

    return ReadAction.compute(() -> {
      JavaLineBreakpointType type = getXBreakpointType();
      if (type == null) return true;
      return type.matchesPosition(this, position);
    });
  }

  @Nullable
  protected JavaLineBreakpointType getXBreakpointType() {
    XBreakpointType<?, P> type = myXBreakpoint.getType();
    // Nashorn breakpoints do not contain JavaLineBreakpointType
    if (type instanceof JavaLineBreakpointType) {
      return (JavaLineBreakpointType)type;
    }
    return null;
  }

  private boolean isInScopeOf(DebugProcessImpl debugProcess, String className) {
    VirtualFile breakpointFile = getVirtualFile();
    if (breakpointFile != null) {
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
      if (fileIndex.isUnderSourceRootOfType(breakpointFile, JavaModuleSourceRootTypes.SOURCES)) {
        if (debugProcess.getSearchScope().contains(breakpointFile)) {
          return true;
        }
        // apply filtering to breakpoints from content sources only, not for sources attached to libraries
        final Collection<VirtualFile> candidates = findClassCandidatesInSourceContent(className, debugProcess.getSearchScope(), fileIndex);
        if (LOG.isDebugEnabled()) {
          LOG.debug("Found " + (candidates == null ? "null" : candidates.size()) + " candidate containing files for class " + className);
        }
        if (candidates == null) {
          // If no candidates are found in scope then assume that class is loaded dynamically and allow breakpoint
          return true;
        }

        // breakpointFile is not in scope here and there are some candidates in scope
        //for (VirtualFile classFile : candidates) {
        //  if (LOG.isDebugEnabled()) {
        //    LOG.debug("Breakpoint file: " + breakpointFile.getPath()+ "; candidate file: " + classFile.getPath());
        //  }
        //  if (breakpointFile.equals(classFile)) {
        //    return true;
        //  }
        //}
        if (LOG.isDebugEnabled()) {
          final GlobalSearchScope scope = debugProcess.getSearchScope();
          final boolean contains = scope.contains(breakpointFile);
          List<VirtualFile> files = ContainerUtil.map(
            JavaPsiFacade.getInstance(myProject).findClasses(className, scope),
            aClass -> aClass.getContainingFile().getVirtualFile());
          List<VirtualFile> allFiles = ContainerUtil.map(
            JavaPsiFacade.getInstance(myProject).findClasses(className, GlobalSearchScope.everythingScope(myProject)),
            aClass -> aClass.getContainingFile().getVirtualFile());
          final VirtualFile contentRoot = fileIndex.getContentRootForFile(breakpointFile);
          final Module module = fileIndex.getModuleForFile(breakpointFile);

          LOG.debug("Did not find '" +
                    className + "' in " + scope +
                    "; contains=" + contains +
                    "; contentRoot=" + contentRoot +
                    "; module = " + module +
                    "; all files in index are: " + files +
                    "; all possible files are: " + allFiles
          );
        }

        return false;
      }
    }
    return true;
  }

  @Nullable
  private Collection<VirtualFile> findClassCandidatesInSourceContent(final String className, final GlobalSearchScope scope, final ProjectFileIndex fileIndex) {
    DebuggerManagerThreadImpl.assertIsManagerThread();
    final int dollarIndex = className.indexOf("$");
    final String topLevelClassName = dollarIndex >= 0 ? className.substring(0, dollarIndex) : className;
    return ReadAction.compute(() -> {
      final PsiClass[] classes = JavaPsiFacade.getInstance(myProject).findClasses(topLevelClassName, scope);
      if (LOG.isDebugEnabled()) {
        LOG.debug("Found " + classes.length + " classes " + topLevelClassName + " in scope " + scope);
      }
      if (classes.length == 0) {
        return null;
      }
      final List<VirtualFile> list = new ArrayList<>(classes.length);
      for (PsiClass aClass : classes) {
        final PsiFile psiFile = aClass.getContainingFile();

        if (LOG.isDebugEnabled()) {
          final StringBuilder msg = new StringBuilder();
          msg.append("Checking class ").append(aClass.getQualifiedName());
          msg.append("\n\t").append("PsiFile=").append(psiFile);
          if (psiFile != null) {
            final VirtualFile vFile = psiFile.getVirtualFile();
            msg.append("\n\t").append("VirtualFile=").append(vFile);
            if (vFile != null) {
              msg.append("\n\t").append("isInSourceContent=").append(fileIndex.isUnderSourceRootOfType(vFile, JavaModuleSourceRootTypes.SOURCES));
            }
          }
          LOG.debug(msg.toString());
        }

        if (psiFile == null) {
          return null;
        }
        final VirtualFile vFile = psiFile.getVirtualFile();
        if (vFile == null || !fileIndex.isUnderSourceRootOfType(vFile, JavaModuleSourceRootTypes.SOURCES)) {
          return null; // this will switch off the check if at least one class is from libraries
        }
        list.add(vFile);
      }
      return list;
    });
  }

  @Override
  public String getShortName() {
    return getDisplayInfoInternal(false, 30);
  }

  @Override
  public String getDisplayName() {
    return getDisplayInfoInternal(true, -1);
  }

  private int getColumnNumberOrZero() {
    var type = getXBreakpointType();
    if (type == null) return 0;
    var xBreakpoint = getXBreakpoint();
    if (!(xBreakpoint instanceof XLineBreakpoint<P>)) return 0;
    final int column = type.getColumn(((XLineBreakpoint<JavaLineBreakpointProperties>)xBreakpoint));
    if (column <= 0) return 0;
    return column + 1;
  }

  private @NlsContexts.Label String getDisplayInfoInternal(boolean showPackageInfo, int totalTextLength) {
    if (isValid()) {
      final int lineNumber = getLineIndex() + 1;
      final int columnNumber = getColumnNumberOrZero();
      String className = getClassName();
      final boolean hasClassInfo = className != null && !className.isEmpty();
      final String methodName = myMethodName;
      final String displayName = methodName != null ? methodName + "()" : null;
      final boolean hasMethodInfo = displayName != null;
      if (hasClassInfo || hasMethodInfo) {
        final StringBuilder info = new StringBuilder();
        boolean isFile = getFileName().equals(className);
        String packageName = null;
        if (hasClassInfo) {
          final int dotIndex = className.lastIndexOf(".");
          if (dotIndex >= 0 && !isFile) {
            packageName = className.substring(0, dotIndex);
            className = className.substring(dotIndex + 1);
          }

          if (totalTextLength != -1) {
            if (className.length() + (hasMethodInfo ? displayName.length() : 0) > totalTextLength + 3) {
              int offset = totalTextLength - (hasMethodInfo ? displayName.length() : 0);
              if (offset > 0 && offset < className.length()) {
                className = className.substring(className.length() - offset);
                info.append("...");
              }
            }
          }

          info.append(className);
        }
        if (hasMethodInfo) {
          if (isFile) {
            info.append(":");
          }
          else if (hasClassInfo) {
            info.append(".");
          }
          info.append(displayName);
        }
        if (showPackageInfo && packageName != null) {
          info.append(" (").append(packageName).append(")");
        }
        if (columnNumber > 0) {
          return JavaDebuggerBundle.message("line.breakpoint.display.name.with.column.and.class.or.method",
                                            lineNumber, columnNumber, info.toString());
        } else {
          return JavaDebuggerBundle.message("line.breakpoint.display.name.with.class.or.method",
                                            lineNumber, info.toString());
        }
      }
      if (columnNumber > 0) {
        return JavaDebuggerBundle.message("line.breakpoint.display.name.with.column", lineNumber, columnNumber);
      } else {
        return JavaDebuggerBundle.message("line.breakpoint.display.name", lineNumber);
      }
    }
    return JavaDebuggerBundle.message("status.breakpoint.invalid");
  }

  @Nullable
  private static String findOwnerMethod(final PsiFile file, final int offset) {
    if (offset < 0 || file instanceof JspFile) {
      return null;
    }
    if (file instanceof PsiClassOwner) {
      return ReadAction.compute(() -> {
        PsiMethod method = DebuggerUtilsEx.findPsiMethod(file, offset);
        return method != null ? method.getName() : null;
      });
    }
    return null;
  }

  @Override
  public String getEventMessage(LocatableEvent event) {
    final Location location = event.location();
    String sourceName = DebuggerUtilsEx.getSourceName(location, e -> getFileName());

    return JavaDebuggerBundle.message(
      "status.line.breakpoint.reached",
      DebuggerUtilsEx.getLocationMethodQName(location),
      sourceName,
      getLineIndex() + 1
    );
  }

  @Override
  public PsiElement getEvaluationElement() {
    return ContextUtil.getContextElement(getSourcePosition());
  }

  public static LineBreakpoint create(@NotNull Project project, XBreakpoint xBreakpoint) {
    LineBreakpoint breakpoint = new LineBreakpoint(project, xBreakpoint);
    return (LineBreakpoint)breakpoint.init();
  }

  //@Override
  //public boolean canMoveTo(SourcePosition position) {
  //  if (!super.canMoveTo(position)) {
  //    return false;
  //  }
  //  final Document document = position.getFile().getViewProvider().getDocument();
  //  return canAddLineBreakpoint(myProject, document, position.getLine());
  //}

  public static boolean canAddLineBreakpoint(Project project, final Document document, final int lineIndex) {
    if (lineIndex < 0 || lineIndex >= document.getLineCount()) {
      return false;
    }
    final BreakpointManager breakpointManager = DebuggerManagerEx.getInstanceEx(project).getBreakpointManager();
    final LineBreakpoint breakpointAtLine = breakpointManager.findBreakpoint(document, document.getLineStartOffset(lineIndex), CATEGORY);
    if (breakpointAtLine != null) {
      // there already exists a line breakpoint at this line
      return false;
    }
    PsiDocumentManager.getInstance(project).commitDocument(document);

    final boolean[] canAdd = new boolean[]{false};
    XDebuggerUtil.getInstance().iterateLine(project, document, lineIndex, element -> {
      if ((element instanceof PsiWhiteSpace) || (PsiTreeUtil.getParentOfType(element, PsiComment.class, false) != null)) {
        return true;
      }
      PsiElement child = element;
      while (element != null) {

        final int offset = element.getTextOffset();
        if (offset >= 0) {
          if (document.getLineNumber(offset) != lineIndex) {
            break;
          }
        }
        child = element;
        element = element.getParent();
      }

      if (child instanceof PsiMethod && child.getTextRange().getEndOffset() >= document.getLineEndOffset(lineIndex)) {
        PsiCodeBlock body = ((PsiMethod)child).getBody();
        if (body == null) {
          canAdd[0] = false;
        }
        else {
          PsiStatement[] statements = body.getStatements();
          canAdd[0] = statements.length > 0 && document.getLineNumber(statements[0].getTextOffset()) == lineIndex;
        }
      }
      else {
        canAdd[0] = true;
      }
      return false;
    });

    return canAdd[0];
  }

  @RequiresBackgroundThread
  @RequiresReadLock
  @Override
  public void reload() {
    super.reload();
    myMethodName = computeMethodName();
  }

  @Nullable
  protected String computeMethodName() {
    SourcePosition position = getSourcePosition();
    if (position != null) {
      return findOwnerMethod(position.getFile(), position.getOffset());
    }
    return null;
  }
}
