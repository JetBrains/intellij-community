/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Class BreakpointManager
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.codeInsight.folding.impl.actions.ExpandRegionAction;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.BreakpointStepMethodFilter;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.impl.DebuggerContextListener;
import com.intellij.debugger.impl.DebuggerManagerImpl;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.ui.JavaDebuggerSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.MultiMap;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.XDebuggerUtil;
import com.intellij.xdebugger.breakpoints.XBreakpointType;
import com.intellij.xdebugger.impl.DebuggerSupport;
import com.intellij.xdebugger.impl.XDebugSessionImpl;
import com.sun.jdi.Field;
import com.sun.jdi.InternalException;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ThreadReference;
import com.sun.jdi.request.*;
import gnu.trove.THashMap;
import gnu.trove.TIntHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.JavaBreakpointAdapter;
import org.jetbrains.java.debugger.breakpoints.JavaBreakpointType;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

public class BreakpointManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.BreakpointManager");

  @NonNls private static final String MASTER_BREAKPOINT_TAGNAME = "master_breakpoint";
  @NonNls private static final String SLAVE_BREAKPOINT_TAGNAME = "slave_breakpoint";
  @NonNls private static final String DEFAULT_SUSPEND_POLICY_ATTRIBUTE_NAME = "default_suspend_policy";
  @NonNls private static final String DEFAULT_CONDITION_STATE_ATTRIBUTE_NAME = "default_condition_enabled";

  @NonNls private static final String RULES_GROUP_NAME = "breakpoint_rules";

  private final Project myProject;
  private AnyExceptionBreakpoint myAnyExceptionBreakpoint;
  private final List<Breakpoint> myBreakpoints = new ArrayList<Breakpoint>(); // breakpoints storage, access should be synchronized
  private final List<EnableBreakpointRule> myBreakpointRules = new ArrayList<EnableBreakpointRule>(); // breakpoint rules
  @Nullable private List<Breakpoint> myBreakpointsListForIteration = null; // another list for breakpoints iteration, unsynchronized access ok
  private final MultiMap<Document, BreakpointWithHighlighter> myDocumentBreakpoints = MultiMap.createSmartList();
  private final Map<String, String> myUIProperties = new LinkedHashMap<String, String>();
  private final Map<Key<? extends Breakpoint>, BreakpointDefaults> myBreakpointDefaults = new LinkedHashMap<Key<? extends Breakpoint>, BreakpointDefaults>();

  private final EventDispatcher<BreakpointManagerListener> myDispatcher = EventDispatcher.create(BreakpointManagerListener.class);

  private final StartupManager myStartupManager;

  private void update(@NotNull List<BreakpointWithHighlighter> breakpoints) {
    final TIntHashSet intHash = new TIntHashSet();
    for (BreakpointWithHighlighter breakpoint : breakpoints) {
      SourcePosition sourcePosition = breakpoint.getSourcePosition();
      breakpoint.reload();

      if (breakpoint.isValid()) {
        if (sourcePosition == null || breakpoint.getSourcePosition().getLine() != sourcePosition.getLine()) {
          fireBreakpointChanged(breakpoint);
        }

        if (intHash.contains(breakpoint.getLineIndex())) {
          remove(breakpoint);
        }
        else {
          intHash.add(breakpoint.getLineIndex());
        }
      }
      else {
        remove(breakpoint);
      }
    }
  }

  private void remove(final BreakpointWithHighlighter breakpoint) {
    DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
      @Override
      public void run() {
        removeBreakpoint(breakpoint);
      }
    });
  }

  public BreakpointManager(@NotNull Project project, @NotNull StartupManager startupManager, @NotNull DebuggerManagerImpl debuggerManager) {
    myProject = project;
    myStartupManager = startupManager;
    debuggerManager.getContextManager().addListener(new DebuggerContextListener() {
      private DebuggerSession myPreviousSession;

      @Override
      public void changeEvent(@NotNull DebuggerContextImpl newContext, int event) {
        if (newContext.getDebuggerSession() != myPreviousSession || event == DebuggerSession.EVENT_DETACHED) {
          updateBreakpointsUI();
          myPreviousSession = newContext.getDebuggerSession();
        }
      }
    });

    if (!project.isDefault()) {
      XDebuggerManager.getInstance(project).getBreakpointManager().addBreakpointListener(
        XBreakpointType.EXTENSION_POINT_NAME.findExtension(JavaBreakpointType.class), new JavaBreakpointAdapter(project), project);
    }
  }

  public void init() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.addEditorMouseListener(new EditorMouseAdapter() {
      @Nullable private EditorMouseEvent myMousePressedEvent;

      @Nullable
      private Breakpoint toggleBreakpoint(final boolean mostSuitingBreakpoint, final int line, boolean temporary) {
        final Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
        if (editor == null) {
          return null;
        }
        final Document document = editor.getDocument();
        final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (!JavaBreakpointType.doCanPutAt(psiFile)) {
          return null;
        }

        if (SystemProperties.getBooleanProperty("java.debugger.xBreakpoint", false) &&
            XBreakpointType.EXTENSION_POINT_NAME.findExtension(JavaBreakpointType.class)
              .canPutAt(psiFile.getVirtualFile(), line, myProject)) {
          return null;
        }

        PsiDocumentManager.getInstance(myProject).commitDocument(document);

        int offset = editor.getCaretModel().getOffset();
        int editorLine = editor.getDocument().getLineNumber(offset);
        if (editorLine != line) {
          if (line < 0 || line >= document.getLineCount()) {
            return null;
          }
          offset = editor.getDocument().getLineStartOffset(line);
        }

        ExpandRegionAction.expandRegionAtCaret(myProject, editor);

        Breakpoint breakpoint = findBreakpoint(document, offset, null);
        if (breakpoint == null) {
          boolean isInsideCompiledClass = StdFileTypes.CLASS.equals(psiFile.getFileType());
          if (mostSuitingBreakpoint || isInsideCompiledClass) {
            breakpoint = addFieldBreakpoint(document, offset);
            if (breakpoint == null) {
              breakpoint = addMethodBreakpoint(document, line);
            }
            if (breakpoint == null && !isInsideCompiledClass) {
              breakpoint = addLineBreakpoint(document, line);
            }
          }
          else {
            breakpoint = addLineBreakpoint(document, line);

            if (breakpoint == null) {
              breakpoint = addMethodBreakpoint(document, line);
            }
          }

          if (breakpoint != null) {
            breakpoint.REMOVE_AFTER_HIT = temporary;
            RequestManagerImpl.createRequests(breakpoint);
          }
          return breakpoint;
        }
        else {
          removeBreakpoint(breakpoint);
          return null;
        }
      }

      private boolean isFromMyProject(Editor editor) {
        FileEditor[] allEditors = FileEditorManager.getInstance(myProject).getAllEditors();
        for (FileEditor ed : allEditors) {
          if (!(ed instanceof TextEditor)) {
            continue;
          }
          if (((TextEditor)ed).getEditor().equals(editor)) {
            return true;
          }
        }
        return false;
      }

      //mousePressed + mouseReleased is a hack to keep selection in editor when shift is pressed
      @Override
      public void mousePressed(@NotNull EditorMouseEvent e) {
        if (MarkupEditorFilterFactory.createIsDiffFilter().avaliableIn(e.getEditor())) return;

        if (e.isConsumed()) return;

        if (e.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA && e.getMouseEvent().isShiftDown()) {
          myMousePressedEvent = e;
          e.consume();
        }
      }

      @Override
      public void mouseReleased(@NotNull EditorMouseEvent e) {
        if (myMousePressedEvent != null) {
          mouseClicked(e);
        }
        myMousePressedEvent = null;
      }

      @Override
      public void mouseClicked(@NotNull final EditorMouseEvent e) {
        if (MarkupEditorFilterFactory.createIsDiffFilter().avaliableIn(e.getEditor())) return;

        if (e.isConsumed()) return;

        if (e.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
          PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Runnable() {
            @Override
            public void run() {
              final Editor editor = e.getEditor();
              if (!isFromMyProject(editor)) {
                return;
              }
              final int line = editor.xyToLogicalPosition(e.getMouseEvent().getPoint()).line;
              final Document document = editor.getDocument();
              if (line < 0 || line >= document.getLineCount()) {
                return;
              }
              MouseEvent event = e.getMouseEvent();
              if (event.isPopupTrigger()) {
                return;
              }
              if (event.getButton() != 1) {
                return;
              }
              if (e.getMouseEvent().isControlDown() || e.getMouseEvent().isMetaDown()) {
                return;
              }

              VirtualFile file = FileDocumentManager.getInstance().getFile(document);
              if (file != null && XDebuggerUtil.getInstance().canPutBreakpointAt(myProject, file, line)) {
                return;
              }
              e.consume();

              DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
                @Override
                public void run() {
                  final boolean suitingBreakpoint = e.getMouseEvent().isAltDown() && !e.getMouseEvent().isShiftDown();
                  final boolean temporary = e.getMouseEvent().isAltDown() && e.getMouseEvent().isShiftDown();

                  final Breakpoint breakpoint = toggleBreakpoint(suitingBreakpoint, line, temporary);


                  if (!e.getMouseEvent().isAltDown() && e.getMouseEvent().isShiftDown() && breakpoint != null) {
                    breakpoint.LOG_EXPRESSION_ENABLED = true;
                    String selection = editor.getSelectionModel().getSelectedText();
                    String text = selection != null ? selection : DebuggerBundle.message("breakpoint.log.message",
                                                                                               breakpoint.getDisplayName());
                    breakpoint.setLogMessage(new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, text));
                    breakpoint.SUSPEND = false;
                    editBreakpoint(breakpoint, editor);


                    //DialogWrapper dialog = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager()
                    //  .createConfigurationDialog(breakpoint, BreakpointPropertiesPanel.CONTROL_LOG_MESSAGE);
                    //dialog.show();
                    //
                    //if (!dialog.isOK()) {
                    //  removeBreakpoint(breakpoint);
                    //}
                  }
                }
              });
            }
          });
        }
      }
    }, myProject);

    eventMulticaster.addDocumentListener(new DocumentAdapter() {
      private final Alarm myUpdateAlarm = new Alarm();

      @Override
      public void documentChanged(@NotNull final DocumentEvent e) {
        final Document document = e.getDocument();
        //noinspection SynchronizeOnThis
        synchronized (BreakpointManager.this) {
          Collection<BreakpointWithHighlighter> breakpoints = myDocumentBreakpoints.get(document);
          if (!breakpoints.isEmpty()) {
            myUpdateAlarm.cancelAllRequests();
            // must create new array in order to avoid "concurrent modification" errors
            final List<BreakpointWithHighlighter> breakpointsToUpdate = new ArrayList<BreakpointWithHighlighter>(breakpoints);
            myUpdateAlarm.addRequest(new Runnable() {
              @Override
              public void run() {
                if (!myProject.isDisposed()) {
                  PsiDocumentManager.getInstance(myProject).commitDocument(document);
                  update(breakpointsToUpdate);
                }
              }
            }, 300, ModalityState.NON_MODAL);
          }
        }
      }
    }, myProject);
  }

  public void editBreakpoint(final Breakpoint breakpoint, final Editor editor) {
    DebuggerInvocationUtil.swingInvokeLater(myProject, new Runnable() {
      @Override
      public void run() {
        final RangeHighlighter highlighter = ((BreakpointWithHighlighter)breakpoint).getHighlighter();
        if (highlighter != null) {
          final GutterIconRenderer renderer = highlighter.getGutterIconRenderer();
          if (renderer != null) {
            DebuggerSupport.getDebuggerSupport(JavaDebuggerSupport.class).getEditBreakpointAction().editBreakpoint(
              myProject, editor, breakpoint, renderer
            );
          }
        }
      }
    });
  }

  @NotNull
  public BreakpointDefaults getBreakpointDefaults(Key<? extends Breakpoint> category) {
    BreakpointDefaults defaults = myBreakpointDefaults.get(category);
    if (defaults == null) {
      defaults = new BreakpointDefaults();
    }
    return defaults;
  }

  public void setBreakpointDefaults(Key<? extends Breakpoint> category, BreakpointDefaults defaults) {
    myBreakpointDefaults.put(category, defaults);
  }


  @Nullable
  public RunToCursorBreakpoint addRunToCursorBreakpoint(Document document, int lineIndex, final boolean ignoreBreakpoints) {
    return RunToCursorBreakpoint.create(myProject, document, lineIndex, ignoreBreakpoints);
  }

  @Nullable
  public StepIntoBreakpoint addStepIntoBreakpoint(@NotNull BreakpointStepMethodFilter filter) {
    return StepIntoBreakpoint.create(myProject, filter);
  }

  @Nullable
  public LineBreakpoint addLineBreakpoint(Document document, int lineIndex) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (!LineBreakpoint.canAddLineBreakpoint(myProject, document, lineIndex)) {
      return null;
    }

    LineBreakpoint breakpoint = LineBreakpoint.create(myProject, document, lineIndex);
    if (breakpoint == null) {
      return null;
    }

    addBreakpoint(breakpoint);
    return breakpoint;
  }

  @Nullable
  public FieldBreakpoint addFieldBreakpoint(Field field, ObjectReference object) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    final FieldBreakpoint fieldBreakpoint = FieldBreakpoint.create(myProject, field, object);
    if (fieldBreakpoint != null) {
      addBreakpoint(fieldBreakpoint);
    }
    return fieldBreakpoint;
  }

  @Nullable
  public FieldBreakpoint addFieldBreakpoint(@NotNull Document document, int offset) {
    PsiField field = FieldBreakpoint.findField(myProject, document, offset);
    if (field == null) {
      return null;
    }

    int line = document.getLineNumber(offset);

    if (document.getLineNumber(field.getNameIdentifier().getTextOffset()) < line) {
      return null;
    }

    return addFieldBreakpoint(document, line, field.getName());
  }

  @Nullable
  public FieldBreakpoint addFieldBreakpoint(Document document, int lineIndex, String fieldName) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    FieldBreakpoint fieldBreakpoint = FieldBreakpoint.create(myProject, document, lineIndex, fieldName);
    if (fieldBreakpoint != null) {
      addBreakpoint(fieldBreakpoint);
    }
    return fieldBreakpoint;
  }

  @NotNull
  public ExceptionBreakpoint addExceptionBreakpoint(@NotNull String exceptionClassName, String packageName) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    ExceptionBreakpoint breakpoint = new ExceptionBreakpoint(myProject, exceptionClassName, packageName);
    addBreakpoint(breakpoint);
    if (LOG.isDebugEnabled()) {
      LOG.debug("ExceptionBreakpoint Added");
    }
    return breakpoint;
  }

  @Nullable
  public MethodBreakpoint addMethodBreakpoint(Document document, int lineIndex) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    MethodBreakpoint breakpoint = MethodBreakpoint.create(myProject, document, lineIndex);
    if (breakpoint == null) {
      return null;
    }

    XDebugSessionImpl.NOTIFICATION_GROUP.createNotification("Method breakpoints may dramatically slow down debugging", MessageType.WARNING).notify(myProject);

    addBreakpoint(breakpoint);
    return breakpoint;
  }

  @Nullable
  public WildcardMethodBreakpoint addMethodBreakpoint(String classPattern, String methodName) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    WildcardMethodBreakpoint breakpoint = WildcardMethodBreakpoint.create(myProject, classPattern, methodName);
    if (breakpoint == null) {
      return null;
    }
    addBreakpoint(breakpoint);
    return breakpoint;
  }

  /**
   * @return null if not found or a breakpoint object
   */
  @NotNull
  public List<BreakpointWithHighlighter> findBreakpoints(final Document document, final int offset) {
    LinkedList<BreakpointWithHighlighter> result = new LinkedList<BreakpointWithHighlighter>();
    ApplicationManager.getApplication().assertIsDispatchThread();
    for (final Breakpoint breakpoint : getBreakpoints()) {
      if (breakpoint instanceof BreakpointWithHighlighter && ((BreakpointWithHighlighter)breakpoint).isAt(document, offset)) {
        result.add((BreakpointWithHighlighter)breakpoint);
      }
    }

    return result;
  }

  @NotNull
  public List<BreakpointWithHighlighter> findBreakpoints(@NotNull Document document, @NotNull TextRange textRange) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    List<BreakpointWithHighlighter> result = new ArrayList<BreakpointWithHighlighter>();
    int startLine = document.getLineNumber(textRange.getStartOffset());
    int endLine = document.getLineNumber(textRange.getEndOffset())+1;
    TextRange lineRange = new TextRange(startLine, endLine);
    for (final Breakpoint breakpoint : getBreakpoints()) {
      if (breakpoint instanceof BreakpointWithHighlighter &&
          lineRange.contains(((BreakpointWithHighlighter)breakpoint).getLineIndex())) {
        result.add((BreakpointWithHighlighter)breakpoint);
      }
    }

    return result;
  }

  /**
   * @param category breakpoint category, null if the category does not matter
   */
  @Nullable
  public <T extends BreakpointWithHighlighter> T findBreakpoint(final Document document, final int offset, @Nullable final Key<T> category) {
    for (BreakpointWithHighlighter breakpointWithHighlighter : myDocumentBreakpoints.get(document)) {
      if (breakpointWithHighlighter.isAt(document, offset) &&
          (category == null || category.equals(breakpointWithHighlighter.getCategory()))) {
        //noinspection unchecked
        return (T)breakpointWithHighlighter;
      }
    }

    for (final Breakpoint breakpoint : getBreakpoints()) {
      if (breakpoint instanceof BreakpointWithHighlighter && ((BreakpointWithHighlighter)breakpoint).isAt(document, offset)) {
        if (category == null || category.equals(breakpoint.getCategory())) {
          //noinspection CastConflictsWithInstanceof,unchecked
          return (T)breakpoint;
        }
      }
    }
    return null;
  }

  public void readExternal(@NotNull final Element parentNode) {
    if (myProject.isOpen()) {
      doRead(parentNode);
    }
    else {
      myStartupManager.registerPostStartupActivity(new Runnable() {
        @Override
        public void run() {
          doRead(parentNode);
        }
      });
    }
  }

  private void doRead(@NotNull final Element parentNode) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      @SuppressWarnings({"HardCodedStringLiteral"})
      public void run() {
        final Map<String, Breakpoint> nameToBreakpointMap = new THashMap<String, Breakpoint>();
        try {
          final List groups = parentNode.getChildren();
          for (final Object group1 : groups) {
            final Element group = (Element)group1;
            if (group.getName().equals(RULES_GROUP_NAME)) {
              continue;
            }
            final String categoryName = group.getName();
            final Key<Breakpoint> breakpointCategory = BreakpointCategory.lookup(categoryName);
            final String defaultPolicy = group.getAttributeValue(DEFAULT_SUSPEND_POLICY_ATTRIBUTE_NAME);
            final boolean conditionEnabled = Boolean.parseBoolean(group.getAttributeValue(DEFAULT_CONDITION_STATE_ATTRIBUTE_NAME, "true"));
            setBreakpointDefaults(breakpointCategory, new BreakpointDefaults(defaultPolicy, conditionEnabled));
            Element anyExceptionBreakpointGroup;
            if (!AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT.equals(breakpointCategory)) {
              // for compatibility with previous format
              anyExceptionBreakpointGroup = group.getChild(AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT.toString());
              final BreakpointFactory factory = BreakpointFactory.getInstance(breakpointCategory);
              if (factory != null) {
                for (final Object o : group.getChildren("breakpoint")) {
                  Element breakpointNode = (Element)o;
                  Breakpoint breakpoint = factory.createBreakpoint(myProject, breakpointNode);
                  breakpoint.readExternal(breakpointNode);
                  addBreakpoint(breakpoint);
                  nameToBreakpointMap.put(breakpoint.getDisplayName(), breakpoint);
                }
              }
            }
            else {
              anyExceptionBreakpointGroup = group;
            }

            if (anyExceptionBreakpointGroup != null) {
              final Element breakpointElement = group.getChild("breakpoint");
              if (breakpointElement != null) {
                getAnyExceptionBreakpoint().readExternal(breakpointElement);
              }
            }

          }
        }
        catch (InvalidDataException ignored) {
        }

        final Element rulesGroup = parentNode.getChild(RULES_GROUP_NAME);
        if (rulesGroup != null) {
          final List rules = rulesGroup.getChildren("rule");
          for (final Object rule1 : rules) {
            final Element rule = (Element)rule1;
            final Element master = rule.getChild(MASTER_BREAKPOINT_TAGNAME);
            if (master == null) {
              continue;
            }
            final Element slave = rule.getChild(SLAVE_BREAKPOINT_TAGNAME);
            if (slave == null) {
              continue;
            }
            final Breakpoint masterBreakpoint = nameToBreakpointMap.get(master.getAttributeValue("name"));
            if (masterBreakpoint == null) {
              continue;
            }
            final Breakpoint slaveBreakpoint = nameToBreakpointMap.get(slave.getAttributeValue("name"));
            if (slaveBreakpoint == null) {
              continue;
            }
            addBreakpointRule(new EnableBreakpointRule(BreakpointManager.this, masterBreakpoint, slaveBreakpoint, "true".equalsIgnoreCase(rule.getAttributeValue("leaveEnabled"))));
          }
        }

        DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
          @Override
          public void run() {
            updateBreakpointsUI();
          }
        });
      }
    });

    myUIProperties.clear();
    final Element props = parentNode.getChild("ui_properties");
    if (props != null) {
      final List children = props.getChildren("property");
      for (Object child : children) {
        Element property = (Element)child;
        final String name = property.getAttributeValue("name");
        final String value = property.getAttributeValue("value");
        if (name != null && value != null) {
          myUIProperties.put(name, value);
        }
      }
    }
  }

  //used in Fabrique
  public synchronized void addBreakpoint(Breakpoint breakpoint) {
    myBreakpoints.add(breakpoint);
    myBreakpointsListForIteration = null;
    if (breakpoint instanceof BreakpointWithHighlighter) {
      BreakpointWithHighlighter breakpointWithHighlighter = (BreakpointWithHighlighter)breakpoint;
      final Document document = breakpointWithHighlighter.getDocument();
      if (document != null) {
        myDocumentBreakpoints.putValue(document, breakpointWithHighlighter);
      }
    }
    myDispatcher.getMulticaster().breakpointsChanged();
  }

  public synchronized void removeBreakpoint(@Nullable final Breakpoint breakpoint) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (breakpoint == null) {
      return;
    }

    if (myBreakpoints.remove(breakpoint)) {
      updateBreakpointRules(breakpoint);
      myBreakpointsListForIteration = null;
      if (breakpoint instanceof BreakpointWithHighlighter) {
        for (Document document : myDocumentBreakpoints.keySet()) {
          if (myDocumentBreakpoints.remove(document, (BreakpointWithHighlighter)breakpoint)) {
            break;
          }
        }
      }
      //we delete breakpoints inside release, so gutter will not fire events to deleted breakpoints
      breakpoint.delete();

      myDispatcher.getMulticaster().breakpointsChanged();
    }
  }

  public void writeExternal(@NotNull final Element parentNode) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        removeInvalidBreakpoints();
        final Map<Key<? extends Breakpoint>, Element> categoryToElementMap = new THashMap<Key<? extends Breakpoint>, Element>();
        for (Key<? extends Breakpoint> category : myBreakpointDefaults.keySet()) {
          final Element group = getCategoryGroupElement(categoryToElementMap, category, parentNode);
          final BreakpointDefaults defaults = getBreakpointDefaults(category);
          group.setAttribute(DEFAULT_SUSPEND_POLICY_ATTRIBUTE_NAME, String.valueOf(defaults.getSuspendPolicy()));
          group.setAttribute(DEFAULT_CONDITION_STATE_ATTRIBUTE_NAME, String.valueOf(defaults.isConditionEnabled()));
        }
        // don't store invisible breakpoints
        for (Breakpoint breakpoint : getBreakpoints()) {
          if (breakpoint.isValid() &&
              (!(breakpoint instanceof BreakpointWithHighlighter) || ((BreakpointWithHighlighter)breakpoint).isVisible())) {
            writeBreakpoint(getCategoryGroupElement(categoryToElementMap, breakpoint.getCategory(), parentNode), breakpoint);
          }
        }
        final AnyExceptionBreakpoint anyExceptionBreakpoint = getAnyExceptionBreakpoint();
        final Element group = getCategoryGroupElement(categoryToElementMap, anyExceptionBreakpoint.getCategory(), parentNode);
        writeBreakpoint(group, anyExceptionBreakpoint);

        final Element rules = new Element(RULES_GROUP_NAME);
        parentNode.addContent(rules);
        for (EnableBreakpointRule myBreakpointRule : myBreakpointRules) {
          writeRule(myBreakpointRule, rules);
        }
      }
    });

    final Element uiProperties = new Element("ui_properties");
    parentNode.addContent(uiProperties);
    for (final String name : myUIProperties.keySet()) {
      Element property = new Element("property");
      uiProperties.addContent(property);
      property.setAttribute("name", name);
      property.setAttribute("value", myUIProperties.get(name));
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void writeRule(@NotNull final EnableBreakpointRule enableBreakpointRule, @NotNull Element element) {
    Element rule = new Element("rule");
    if (enableBreakpointRule.isLeaveEnabled()) {
      rule.setAttribute("leaveEnabled", Boolean.toString(true));
    }
    element.addContent(rule);
    writeRuleBreakpoint(rule, MASTER_BREAKPOINT_TAGNAME, enableBreakpointRule.getMasterBreakpoint());
    writeRuleBreakpoint(rule, SLAVE_BREAKPOINT_TAGNAME, enableBreakpointRule.getSlaveBreakpoint());
  }

  @SuppressWarnings({"HardCodedStringLiteral"}) private static void writeRuleBreakpoint(@NotNull final Element element, final String tagName, @NotNull final Breakpoint breakpoint) {
    Element master = new Element(tagName);
    element.addContent(master);
    master.setAttribute("name", breakpoint.getDisplayName());
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void writeBreakpoint(@NotNull final Element group, @NotNull final Breakpoint breakpoint) {
    Element breakpointNode = new Element("breakpoint");
    group.addContent(breakpointNode);
    try {
      breakpoint.writeExternal(breakpointNode);
    }
    catch (WriteExternalException e) {
      LOG.error(e);
    }
  }

  private static <T extends Breakpoint> Element getCategoryGroupElement(@NotNull final Map<Key<? extends Breakpoint>, Element> categoryToElementMap, @NotNull final Key<T> category, @NotNull final Element parentNode) {
    Element group = categoryToElementMap.get(category);
    if (group == null) {
      group = new Element(category.toString());
      categoryToElementMap.put(category, group);
      parentNode.addContent(group);
    }
    return group;
  }

  private void removeInvalidBreakpoints() {
    ArrayList<Breakpoint> toDelete = new ArrayList<Breakpoint>();

    for (Breakpoint breakpoint : getBreakpoints()) {
      if (!breakpoint.isValid()) {
        toDelete.add(breakpoint);
      }
    }

    for (final Breakpoint aToDelete : toDelete) {
      removeBreakpoint(aToDelete);
    }
  }

  /**
   * @return breakpoints of one of the category:
   *         LINE_BREAKPOINTS, EXCEPTION_BREAKPOINTS, FIELD_BREAKPOINTS, METHOD_BREAKPOINTS
   */
  public <T extends Breakpoint> Breakpoint[] getBreakpoints(@NotNull final Key<T> category) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    removeInvalidBreakpoints();

    final ArrayList<Breakpoint> breakpoints = new ArrayList<Breakpoint>();
    for (Breakpoint breakpoint : getBreakpoints()) {
      if (category.equals(breakpoint.getCategory())) {
        breakpoints.add(breakpoint);
      }
    }

    return breakpoints.toArray(new Breakpoint[breakpoints.size()]);
  }

  @NotNull
  public synchronized List<Breakpoint> getBreakpoints() {
    if (myBreakpointsListForIteration == null) {
      myBreakpointsListForIteration = new ArrayList<Breakpoint>(myBreakpoints.size() + 1);
      myBreakpointsListForIteration.addAll(myBreakpoints);
      myBreakpointsListForIteration.add(getAnyExceptionBreakpoint());
    }
    return myBreakpointsListForIteration;
  }

  public AnyExceptionBreakpoint getAnyExceptionBreakpoint() {
    if (myAnyExceptionBreakpoint == null) {
      myAnyExceptionBreakpoint = new AnyExceptionBreakpoint(myProject);
    }
    return myAnyExceptionBreakpoint;
  }

  //interaction with RequestManagerImpl
  public void disableBreakpoints(@NotNull final DebugProcessImpl debugProcess) {
    final List<Breakpoint> breakpoints = getBreakpoints();
    if (!breakpoints.isEmpty()) {
      final RequestManagerImpl requestManager = debugProcess.getRequestsManager();
      for (Breakpoint breakpoint : breakpoints) {
        breakpoint.markVerified(requestManager.isVerified(breakpoint));
        requestManager.deleteRequest(breakpoint);
      }
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          updateBreakpointsUI();
        }
      });
    }
  }

  public void enableBreakpoints(final DebugProcessImpl debugProcess) {
    final List<Breakpoint> breakpoints = getBreakpoints();
    if (!breakpoints.isEmpty()) {
      for (Breakpoint breakpoint : breakpoints) {
        breakpoint.markVerified(false); // clean cached state
        breakpoint.createRequest(debugProcess);
      }
      SwingUtilities.invokeLater(new Runnable() {
        @Override
        public void run() {
          updateBreakpointsUI();
        }
      });
    }
  }

  public void applyThreadFilter(@NotNull final DebugProcessImpl debugProcess, @Nullable ThreadReference newFilterThread) {
    final RequestManagerImpl requestManager = debugProcess.getRequestsManager();
    final ThreadReference oldFilterThread = requestManager.getFilterThread();
    if (Comparing.equal(newFilterThread, oldFilterThread)) {
      // the filter already added
      return;
    }
    requestManager.setFilterThread(newFilterThread);
    if (newFilterThread == null || oldFilterThread != null) {
      final List<Breakpoint> breakpoints = getBreakpoints();
      for (Breakpoint breakpoint : breakpoints) {
        if (LineBreakpoint.CATEGORY.equals(breakpoint.getCategory()) || MethodBreakpoint.CATEGORY.equals(breakpoint.getCategory())) {
          requestManager.deleteRequest(breakpoint);
          breakpoint.createRequest(debugProcess);
        }
      }
    }
    else {
      // important! need to add filter to _existing_ requests, otherwise Requestor->Request mapping will be lost
      // and debugger trees will not be restored to original state
      abstract class FilterSetter <T extends EventRequest> {
         void applyFilter(@NotNull final List<T> requests, final ThreadReference thread) {
          for (T request : requests) {
            try {
              final boolean wasEnabled = request.isEnabled();
              if (wasEnabled) {
                request.disable();
              }
              addFilter(request, thread);
              if (wasEnabled) {
                request.enable();
              }
            }
            catch (InternalException e) {
              LOG.info(e);
            }
          }
        }
        protected abstract void addFilter(final T request, final ThreadReference thread);
      }

      final EventRequestManager eventRequestManager = requestManager.getVMRequestManager();

      new FilterSetter<BreakpointRequest>() {
        @Override
        protected void addFilter(@NotNull final BreakpointRequest request, final ThreadReference thread) {
          request.addThreadFilter(thread);
        }
      }.applyFilter(eventRequestManager.breakpointRequests(), newFilterThread);

      new FilterSetter<MethodEntryRequest>() {
        @Override
        protected void addFilter(@NotNull final MethodEntryRequest request, final ThreadReference thread) {
          request.addThreadFilter(thread);
        }
      }.applyFilter(eventRequestManager.methodEntryRequests(), newFilterThread);

      new FilterSetter<MethodExitRequest>() {
        @Override
        protected void addFilter(@NotNull final MethodExitRequest request, final ThreadReference thread) {
          request.addThreadFilter(thread);
        }
      }.applyFilter(eventRequestManager.methodExitRequests(), newFilterThread);
    }
  }

  public void updateAllRequests() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    List<Breakpoint> breakpoints = getBreakpoints();
    for (Breakpoint breakpoint : breakpoints) {
      fireBreakpointChanged(breakpoint);
    }
  }

  public void updateBreakpointsUI() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    for (Breakpoint breakpoint : getBreakpoints()) {
      breakpoint.updateUI();
    }
  }

  public void reloadBreakpoints() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    for (Breakpoint breakpoint : getBreakpoints()) {
      breakpoint.reload();
    }
  }

  public void addBreakpointManagerListener(@NotNull BreakpointManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeBreakpointManagerListener(@NotNull BreakpointManagerListener listener) {
    myDispatcher.removeListener(listener);
  }
  
  private boolean myAllowMulticasting = true;
  private final Alarm myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  public void fireBreakpointChanged(Breakpoint breakpoint) {
    RequestManagerImpl.updateRequests(breakpoint);
    if (myAllowMulticasting) {
      // can be invoked from non-AWT thread
      myAlarm.cancelAllRequests();
      final Runnable runnable = new Runnable() {
        @Override
        public void run() {
          myAlarm.addRequest(new Runnable() {
            @Override
            public void run() {
              myDispatcher.getMulticaster().breakpointsChanged();
            }
          }, 100);
        }
      };
      if (ApplicationManager.getApplication().isDispatchThread()) {
        runnable.run();
      }
      else {
        SwingUtilities.invokeLater(runnable);
      }
    }
  }

  public void setBreakpointEnabled(@NotNull final Breakpoint breakpoint, final boolean enabled) {
    if (breakpoint.ENABLED != enabled) {
      breakpoint.ENABLED = enabled;
      fireBreakpointChanged(breakpoint);
      breakpoint.updateUI();
    }
  }
  
  public void addBreakpointRule(@NotNull EnableBreakpointRule rule) {
    rule.init();
    myBreakpointRules.add(rule);
  }
  
  public boolean removeBreakpointRule(@NotNull EnableBreakpointRule rule) {
    final boolean removed = myBreakpointRules.remove(rule);
    if (removed) {
      rule.dispose();
    }
    return removed;
  }
  
  public boolean removeBreakpointRule(@NotNull Breakpoint slaveBreakpoint) {
    for (final EnableBreakpointRule rule : myBreakpointRules) {
      if (slaveBreakpoint.equals(rule.getSlaveBreakpoint())) {
        removeBreakpointRule(rule);
        return true;
      }
    }
    return false;
  }

  private void updateBreakpointRules(@NotNull Breakpoint removedBreakpoint) {
    for (Iterator<EnableBreakpointRule> it = myBreakpointRules.iterator(); it.hasNext();) {
      final EnableBreakpointRule rule = it.next();
      if (removedBreakpoint.equals(rule.getMasterBreakpoint()) || removedBreakpoint.equals(rule.getSlaveBreakpoint())) {
        it.remove();
      }
    }
  }

  public void processBreakpointHit(@NotNull final Breakpoint breakpoint) {
    for (final EnableBreakpointRule rule : myBreakpointRules) {
      rule.processBreakpointHit(breakpoint);
    }
  }

  public void setInitialBreakpointsState() {
    myAllowMulticasting = false;
    for (final EnableBreakpointRule myBreakpointRule : myBreakpointRules) {
      myBreakpointRule.init();
    }
    myAllowMulticasting = true;
    if (!myBreakpointRules.isEmpty()) {
      IJSwingUtilities.invoke(new Runnable() {
        @Override
        public void run() {
          myDispatcher.getMulticaster().breakpointsChanged();
        }
      });
    }
  }
  
  @Nullable
  public Breakpoint findMasterBreakpoint(@NotNull Breakpoint dependentBreakpoint) {
    for (final EnableBreakpointRule rule : myBreakpointRules) {
      if (dependentBreakpoint.equals(rule.getSlaveBreakpoint())) {
        return rule.getMasterBreakpoint();
      }
    }
    return null;
  }

  @Nullable
  public EnableBreakpointRule findBreakpointRule(@NotNull Breakpoint dependentBreakpoint) {
    for (final EnableBreakpointRule rule : myBreakpointRules) {
      if (dependentBreakpoint.equals(rule.getSlaveBreakpoint())) {
        return rule;
      }
    }
    return null;
  }

  public String getProperty(String name) {
    return myUIProperties.get(name);
  }
  
  public String setProperty(String name, String value) {
    return myUIProperties.put(name, value);
  }
}
