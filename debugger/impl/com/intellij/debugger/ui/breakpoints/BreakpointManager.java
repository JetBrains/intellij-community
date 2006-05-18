/*
 * Class BreakpointManager
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.actions.ViewBreakpointsAction;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerUtils;
import com.intellij.debugger.engine.evaluation.CodeFragmentKind;
import com.intellij.debugger.engine.evaluation.TextWithImports;
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl;
import com.intellij.debugger.engine.requests.RequestManagerImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.util.Alarm;
import com.intellij.util.EventDispatcher;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.containers.HashMap;
import com.sun.jdi.Field;
import com.sun.jdi.ObjectReference;
import gnu.trove.TIntHashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.*;

public class BreakpointManager implements JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.breakpoints.BreakpointManager");

  private static final @NonNls String RULES_GROUP_NAME = "breakpoint_rules";
  private final Project myProject;
  private AnyExceptionBreakpoint myAnyExceptionBreakpoint;
  private List<Breakpoint> myBreakpoints = new ArrayList<Breakpoint>(); // breakpoints storage, access should be synchronized
  private List<EnableBreakpointRule> myBreakpointRules = new ArrayList<EnableBreakpointRule>(); // breakpoint rules
  private List<Breakpoint> myBreakpointsListForIteration = null; // another list for breakpoints iteration, unsynchronized access ok
  private Map<Document, List<BreakpointWithHighlighter>> myDocumentBreakpoints = new HashMap<Document, List<BreakpointWithHighlighter>>();
  private Map<String, String> myUIProperties = new java.util.HashMap<String, String>();

  private BreakpointsConfigurationDialogFactory myBreakpointsConfigurable;
  private EditorMouseListener myEditorMouseListener;

  private final EventDispatcher<BreakpointManagerListener> myDispatcher = EventDispatcher.create(BreakpointManagerListener.class);

  private StartupManager myStartupManager;

  private final DocumentListener myDocumentListener = new DocumentAdapter() {
    Alarm myUpdateAlarm = new Alarm();

    public void documentChanged(final DocumentEvent e) {
      final Document document = e.getDocument();
      synchronized (BreakpointManager.this) {
        List<BreakpointWithHighlighter> breakpoints = myDocumentBreakpoints.get(document);

        if(breakpoints != null) {
          myUpdateAlarm.cancelAllRequests();
          // must create new array in order to avoid "concurrent modification" errors
          final List<BreakpointWithHighlighter> breakpointsToUpdate = new ArrayList<BreakpointWithHighlighter>(breakpoints);
          myUpdateAlarm.addRequest(new Runnable() {
            public void run() {
              if(!myProject.isDisposed()) {
                PsiDocumentManager.getInstance(myProject).commitDocument(document);
                update(breakpointsToUpdate);
              }
            }
          }, 300, ModalityState.NON_MMODAL);
        }
      }
    }
  };
  private static final @NonNls String MASTER_BREAKPOINT_TAGNAME = "master_breakpoint";
  private static final @NonNls String SLAVE_BREAKPOINT_TAGNAME = "slave_breakpoint";

  private void update(List<BreakpointWithHighlighter> breakpoints) {
    final TIntHashSet intHash = new TIntHashSet();

    for (BreakpointWithHighlighter breakpoint : breakpoints) {
      SourcePosition sourcePosition = breakpoint.getSourcePosition();
      breakpoint.reload();

      if (breakpoint.isValid()) {
        if (breakpoint.getSourcePosition().getLine() != sourcePosition.getLine()) {
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

  /*
  // todo: not needed??
  private void setInvalid(final BreakpointWithHighlighter breakpoint) {
    Collection<DebuggerSession> sessions = DebuggerManagerEx.getInstanceEx(myProject).getSessions();

    for (Iterator<DebuggerSession> iterator = sessions.iterator(); iterator.hasNext();) {
      DebuggerSession session = iterator.next();
      final DebugProcessImpl process = session.getProcess();
      process.getManagerThread().invokeLater(new DebuggerCommandImpl() {
        protected void action() throws Exception {
          process.getRequestsManager().deleteRequest(breakpoint);
          process.getRequestsManager().setInvalid(breakpoint, "Source code changed");
          breakpoint.updateUI();
        }
      });
    }
  }
  */

  private void remove(final BreakpointWithHighlighter breakpoint) {
    DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
      public void run() {
        removeBreakpoint(breakpoint);
      }
    });
  }

  public BreakpointManager(Project project, StartupManager startupManager, DebuggerManagerImpl debuggerManager) {
    myProject = project;
    myStartupManager = startupManager;
    myAnyExceptionBreakpoint = new AnyExceptionBreakpoint(project);
    debuggerManager.getContextManager().addListener(new DebuggerContextListener() {
      private DebuggerSession myPreviousSession;

      public void changeEvent(DebuggerContextImpl newContext, int event) {
        if (newContext.getDebuggerSession() != myPreviousSession || event == DebuggerSession.EVENT_DETACHED) {
          updateBreakpointsUI();
          myPreviousSession = newContext.getDebuggerSession();
        }
      }
    });
  }

  public void init() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    myEditorMouseListener = new EditorMouseAdapter() {
      private EditorMouseEvent myMousePressedEvent;

      private @Nullable Breakpoint toggleBreakpoint(final boolean mostSuitingBreakpoint, final int line) {
        final Editor editor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
        if (editor == null) {
          return null;
        }
        final Document document = editor.getDocument();
        final PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
        if (!DebuggerUtils.supportsJVMDebugging(psiFile.getFileType())) {
          return null;
        }
        PsiDocumentManager.getInstance(myProject).commitDocument(document);

        int offset = editor.getCaretModel().getOffset();
        int editorLine = editor.getDocument().getLineNumber(offset);
        if(editorLine != line) {
          if (line < 0 || line >= document.getLineCount()) {
            return null;
          }
          offset = editor.getDocument().getLineStartOffset(line);
        }

        Breakpoint breakpoint = findBreakpoint(document, offset);
        if (breakpoint == null) {
          if(mostSuitingBreakpoint) {
            breakpoint = addFieldBreakpoint(document, offset);
            if (breakpoint == null) {
              breakpoint = addMethodBreakpoint(document, line);
            }
            if (breakpoint == null) {
              breakpoint = addLineBreakpoint(document, line);
            }
          }
          else {
            breakpoint = addLineBreakpoint(document, line);

            if (breakpoint == null) {
              breakpoint = addMethodBreakpoint(document, line);
            }
          }

          if(breakpoint != null) {
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
        for (int idx = 0; idx < allEditors.length; idx++) {
          FileEditor ed = allEditors[idx];
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
      public void mousePressed(EditorMouseEvent e) {
        if (MarkupEditorFilterFactory.createIsDiffFilter().avaliableIn(e.getEditor())) return;

        if (e.isConsumed()) return;

        if (e.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA && e.getMouseEvent().isShiftDown()) {
          myMousePressedEvent = e;
          e.consume();
        }
      }

      public void mouseReleased(EditorMouseEvent e) {
        if(myMousePressedEvent != null) {
          mouseClicked(e);
        }
        myMousePressedEvent = null;
      }

      public void mouseClicked(final EditorMouseEvent e) {
        if (MarkupEditorFilterFactory.createIsDiffFilter().avaliableIn(e.getEditor())) return;

        if (e.isConsumed()) return;

        if (e.getArea() == EditorMouseEventArea.LINE_MARKERS_AREA) {
          PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Runnable() {
            public void run() {
              final Editor editor = e.getEditor();
              if (!isFromMyProject(editor)) {
                return;
              }
              final int line = editor.xyToLogicalPosition(e.getMouseEvent().getPoint()).line;
              if (line < 0 || line >= editor.getDocument().getLineCount()) {
                return;
              }
              MouseEvent event = e.getMouseEvent();
              if (event.isPopupTrigger()) {
                return;
              }
              if (event.getButton() != 1) {
                return;
              }

              e.consume();

              DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
                public void run() {
                  Breakpoint breakpoint = toggleBreakpoint(e.getMouseEvent().isAltDown(), line);

                  if(e.getMouseEvent().isShiftDown() && breakpoint != null) {
                    breakpoint.LOG_EXPRESSION_ENABLED = true;
                    final TextWithImports logMessage = DebuggerUtilsEx.getEditorText(editor);
                    breakpoint.setLogMessage(logMessage != null? logMessage : new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, DebuggerBundle.message("breakpoint.log.message", breakpoint.getDisplayName())));
                    breakpoint.SUSPEND_POLICY = DebuggerSettings.SUSPEND_NONE;

                    DialogWrapper dialog = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().createConfigurationDialog(breakpoint, BreakpointPropertiesPanel.CONTROL_LOG_MESSAGE);
                    dialog.show();

                    if(!dialog.isOK()) {
                      removeBreakpoint(breakpoint);
                    }
                  }
                }
              });
            }
          });
        }
      }
    };

    eventMulticaster.addEditorMouseListener(myEditorMouseListener);
    eventMulticaster.addDocumentListener(myDocumentListener);
  }

  public void dispose() {
    EditorEventMulticaster eventMulticaster = EditorFactory.getInstance().getEventMulticaster();
    eventMulticaster.removeEditorMouseListener(myEditorMouseListener);
    eventMulticaster.removeDocumentListener(myDocumentListener);
  }

  public DialogWrapper createConfigurationDialog(Breakpoint initialBreakpoint, String selectComponent) {
    if (myBreakpointsConfigurable == null) {
      myBreakpointsConfigurable = new BreakpointsConfigurationDialogFactory(myProject);
    }
    return myBreakpointsConfigurable.createDialog(initialBreakpoint, selectComponent);
  }

  public LineBreakpoint addRunToCursorBreakpoint(Document document, int lineIndex) {
    return LineBreakpoint.create(myProject, document, lineIndex, false);
  }

  public LineBreakpoint addLineBreakpoint(Document document, int lineIndex) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    if (!LineBreakpoint.canAddLineBreakpoint(myProject, document, lineIndex)) return null;

    LineBreakpoint breakpoint = LineBreakpoint.create(myProject, document, lineIndex, true);
    if (breakpoint == null) {
      return null;
    }

    addBreakpoint(breakpoint);
    return breakpoint;
  }

  public FieldBreakpoint addFieldBreakpoint(Field field, ObjectReference object) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    FieldBreakpoint fieldBreakpoint = FieldBreakpoint.create(myProject, field, object);
    if (fieldBreakpoint != null) {
      addBreakpoint(fieldBreakpoint);
    }
    return fieldBreakpoint;
  }

  public FieldBreakpoint addFieldBreakpoint(Document document, int offset) {
    PsiField field = FieldBreakpoint.findField(myProject, document, offset);
    if (field == null) return null;

    int line = document.getLineNumber(offset);

    if (document.getLineNumber(field.getNameIdentifier().getTextOffset()) < line) return null;

    return addFieldBreakpoint(document, line, field.getName());
  }

  public FieldBreakpoint addFieldBreakpoint(Document document, int lineIndex, String fieldName) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    FieldBreakpoint fieldBreakpoint = FieldBreakpoint.create(myProject, document, lineIndex, fieldName);
    if (fieldBreakpoint != null) {
      addBreakpoint(fieldBreakpoint);
    }
    return fieldBreakpoint;
  }

  public ExceptionBreakpoint addExceptionBreakpoint(String exceptionClassName, String packageName) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    LOG.assertTrue(exceptionClassName != null);
    ExceptionBreakpoint breakpoint = new ExceptionBreakpoint(myProject, exceptionClassName, packageName);
    addBreakpoint(breakpoint);
    if (LOG.isDebugEnabled()) {
      LOG.debug("ExceptionBreakpoint Added");
    }
    return breakpoint;
  }

  public MethodBreakpoint addMethodBreakpoint(Document document, int lineIndex) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    MethodBreakpoint breakpoint = MethodBreakpoint.create(myProject, document, lineIndex);
    if (breakpoint == null) {
      return null;
    }
    addBreakpoint(breakpoint);
    return breakpoint;
  }

  public WildcardMethodBreakpoint addMethodBreakpoint(String classPattern, String methodName) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
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
  public List<BreakpointWithHighlighter> findBreakpoints(final Document document, final int offset) {
    LinkedList<BreakpointWithHighlighter> result = new LinkedList<BreakpointWithHighlighter>();

    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (Iterator iterator = getBreakpoints().iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = (Breakpoint)iterator.next();
      if (breakpoint instanceof BreakpointWithHighlighter && ((BreakpointWithHighlighter)breakpoint).isAt(document, offset)) {
        result.add((BreakpointWithHighlighter)breakpoint);
      }
    }

    return result;
  }

  public BreakpointWithHighlighter findBreakpoint(final Document document, final int offset) {
    List<BreakpointWithHighlighter> breakpoints = findBreakpoints(document, offset);
    return breakpoints.isEmpty() ? null : breakpoints.get(0);
  }

  public LineBreakpoint findLineBreakpoint(final Document document, final int offset) {
    List<BreakpointWithHighlighter> breakpoints = findBreakpoints(document, offset);
    for (Iterator<BreakpointWithHighlighter> iterator = breakpoints.iterator(); iterator.hasNext();) {
      BreakpointWithHighlighter breakpointWithHighlighter = iterator.next();
      if(breakpointWithHighlighter instanceof LineBreakpoint) return (LineBreakpoint)breakpointWithHighlighter;
    }
    return null;
  }

  public MethodBreakpoint findMethodBreakpoint(final Document document, final int offset) {
    List<BreakpointWithHighlighter> breakpoints = findBreakpoints(document, offset);
    for (Iterator<BreakpointWithHighlighter> iterator = breakpoints.iterator(); iterator.hasNext();) {
      BreakpointWithHighlighter breakpointWithHighlighter = iterator.next();
      if(breakpointWithHighlighter instanceof MethodBreakpoint) return (MethodBreakpoint)breakpointWithHighlighter;
    }
    return null;
  }

  public FieldBreakpoint findFieldBreakpoint(final Document document, final int offset) {
    List<BreakpointWithHighlighter> breakpoints = findBreakpoints(document, offset);
    for (Iterator<BreakpointWithHighlighter> iterator = breakpoints.iterator(); iterator.hasNext();) {
      BreakpointWithHighlighter breakpointWithHighlighter = iterator.next();
      if(breakpointWithHighlighter instanceof FieldBreakpoint) return (FieldBreakpoint)breakpointWithHighlighter;
    }
    return null;
  }

  public void readExternal(final Element parentNode) throws InvalidDataException {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    myStartupManager.registerPostStartupActivity(new Runnable() {
      @SuppressWarnings({"HardCodedStringLiteral"}) public void run() {
        PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Runnable() {
          @SuppressWarnings({"HardCodedStringLiteral"})
          public void run() {
            final Map<String, Breakpoint> nameToBreakpointMap = new java.util.HashMap<String, Breakpoint>();
            try {
              final List groups = parentNode.getChildren();
              for (Iterator it = groups.iterator(); it.hasNext();) {
                final Element group = (Element)it.next();
                final String category = group.getName();
                Element anyExceptionBreakpointGroup = null;
                if (!AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT.equals(category)) {
                  // for compatibility with previous format
                  anyExceptionBreakpointGroup = group.getChild(AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT);
                  final BreakpointFactory factory = BreakpointFactory.getInstance(category);
                  if (factory != null) {
                    for (Iterator i = group.getChildren("breakpoint").iterator(); i.hasNext();) {
                      Element breakpointNode = (Element)i.next();
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
                    myAnyExceptionBreakpoint.readExternal(breakpointElement);
                  }
                }
                
              }
            }
            catch (InvalidDataException e) {
            }

            final Element rulesGroup = parentNode.getChild(RULES_GROUP_NAME);
            if (rulesGroup != null) {
              final List rules = rulesGroup.getChildren("rule");
              for (Iterator it = rules.iterator(); it.hasNext();) {
                final Element rule = (Element)it.next();
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
                addBreakpointRule(new EnableBreakpointRule(BreakpointManager.this, masterBreakpoint, slaveBreakpoint));
              }
            }

            DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
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
    });

  }

  //used in Fabrique
  public synchronized void addBreakpoint(Breakpoint breakpoint) {
    myBreakpoints.add(breakpoint);
    myBreakpointsListForIteration = null;
    if(breakpoint instanceof BreakpointWithHighlighter) {
      BreakpointWithHighlighter breakpointWithHighlighter = ((BreakpointWithHighlighter) breakpoint);
      Document document = breakpointWithHighlighter.getDocument();
      if(document != null) {
        List<BreakpointWithHighlighter> breakpoints = myDocumentBreakpoints.get(document);

        if(breakpoints == null) {
          breakpoints = new ArrayList<BreakpointWithHighlighter>();
          myDocumentBreakpoints.put(document, breakpoints);
        }
        breakpoints.add(breakpointWithHighlighter);
      }
    }
    myDispatcher.getMulticaster().breakpointsChanged();
  }

  public synchronized void removeBreakpoint(final Breakpoint breakpoint) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    if (breakpoint == null) {
      return;
    }

    if (myBreakpoints.remove(breakpoint)) {
      updateBreakpointRules(breakpoint);
      myBreakpointsListForIteration = null;
      if(breakpoint instanceof BreakpointWithHighlighter) {
        //breakpoint.saveToString() may be invalid

        for (Iterator<Document> iterator = myDocumentBreakpoints.keySet().iterator(); iterator.hasNext();) {
          final Document document = iterator.next();
          final List<BreakpointWithHighlighter> documentBreakpoints = myDocumentBreakpoints.get(document);
          final boolean reallyRemoved = documentBreakpoints.remove(breakpoint);
          if (reallyRemoved) {
            if (documentBreakpoints.isEmpty()) {
              myDocumentBreakpoints.remove(document);
            }
            break;
          }
        }
      }
      //we delete breakpoints inside release, so gutter will not fire events to deleted breakpoints
      breakpoint.delete();

      myDispatcher.getMulticaster().breakpointsChanged();
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"}) public void writeExternal(final Element parentNode) throws WriteExternalException {
    WriteExternalException ex = PsiDocumentManager.getInstance(myProject).commitAndRunReadAction(new Computable<WriteExternalException>() {
      public WriteExternalException compute() {
        try {
          removeInvalidBreakpoints();
          final Map<String, Element> categoryToElementMap = new java.util.HashMap<String, Element>();
          for (Iterator<Breakpoint> it = getBreakpoints().iterator(); it.hasNext(); ) {
            final Breakpoint breakpoint = it.next();
            final String category = breakpoint.getCategory();
            final Element group = getCategoryGroupElement(categoryToElementMap, category, parentNode);
            if(breakpoint.isValid()) {
              writeBreakpoint(group, breakpoint);
            }
          }
          final Element group = getCategoryGroupElement(categoryToElementMap, myAnyExceptionBreakpoint.getCategory(), parentNode);
          writeBreakpoint(group, myAnyExceptionBreakpoint);
          
          final Element rules = new Element(RULES_GROUP_NAME);
          parentNode.addContent(rules);
          for (Iterator<EnableBreakpointRule> it = myBreakpointRules.iterator(); it.hasNext();) {
            writeRule(it.next(), rules);
          }
          
          return null;
        }
        catch (WriteExternalException e) {
          return e;
        }
      }
    });
    if (ex != null) {
      throw ex;
    }
    
    final Element props = new Element("ui_properties");
    parentNode.addContent(props);
    for (Iterator<String> it = myUIProperties.keySet().iterator(); it.hasNext();) {
      final String name = it.next();
      final String value = myUIProperties.get(name);
      final Element property = new Element("property");
      props.addContent(property);
      property.setAttribute("name", name);
      property.setAttribute("value", value);
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"}) private static void writeRule(final EnableBreakpointRule enableBreakpointRule, Element element) {
    Element rule = new Element("rule");
    element.addContent(rule);
    writeRuleBreakpoint(rule, MASTER_BREAKPOINT_TAGNAME, enableBreakpointRule.getMasterBreakpoint());
    writeRuleBreakpoint(rule, SLAVE_BREAKPOINT_TAGNAME, enableBreakpointRule.getSlaveBreakpoint());
  }

  @SuppressWarnings({"HardCodedStringLiteral"}) private static void writeRuleBreakpoint(final Element element, final String tagName, final Breakpoint breakpoint) {
    Element master = new Element(tagName);
    element.addContent(master);
    master.setAttribute("name", breakpoint.getDisplayName());
  }

  @SuppressWarnings({"HardCodedStringLiteral"}) private void writeBreakpoint(final Element group, final Breakpoint breakpoint) throws WriteExternalException {
    Element breakpointNode = new Element("breakpoint");
    group.addContent(breakpointNode);
    breakpoint.writeExternal(breakpointNode);
  }

  private Element getCategoryGroupElement(final Map<String, Element> categoryToElementMap, final String category, final Element parentNode) {
    Element group = categoryToElementMap.get(category);
    if (group == null) {
      group = new Element(category);
      categoryToElementMap.put(category, group);
      parentNode.addContent(group);
    }
    return group;
  }

  private void removeInvalidBreakpoints() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    ArrayList<Breakpoint> toDelete = new ArrayList<Breakpoint>();

    for (Iterator it = getBreakpoints().listIterator(); it.hasNext();) {
      Breakpoint breakpoint = (Breakpoint)it.next();
      if (!breakpoint.isValid()) {
        toDelete.add(breakpoint);
      }
    }

    for (Iterator<Breakpoint> iterator = toDelete.iterator(); iterator.hasNext();) {
      removeBreakpoint(iterator.next());
    }
  }

  /**
   * @return breakpoints of one of the category:
   *         LINE_BREAKPOINTS, EXCEPTION_BREKPOINTS, FIELD_BREAKPOINTS, METHOD_BREAKPOINTS
   */
  public Breakpoint[] getBreakpoints(final String category) {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    removeInvalidBreakpoints();

    final ArrayList<Breakpoint> breakpoints = new ArrayList<Breakpoint>();

    for (Iterator<Breakpoint> iterator = getBreakpoints().iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      if(category.equals(breakpoint.getCategory())) {
        breakpoints.add(breakpoint);
      }
    }

    return breakpoints.toArray(new Breakpoint[breakpoints.size()]);
  }

  public synchronized List<Breakpoint> getBreakpoints() {
    if (myBreakpointsListForIteration == null) {
      myBreakpointsListForIteration = new ArrayList<Breakpoint>(myBreakpoints.size() + 1);
      myBreakpointsListForIteration.addAll(myBreakpoints);
      myBreakpointsListForIteration.add(myAnyExceptionBreakpoint);
    }
    return myBreakpointsListForIteration;
  }

  public AnyExceptionBreakpoint getAnyExceptionBreakpoint() {
    return myAnyExceptionBreakpoint;
  }

  ActionGroup createMenuActions(final Breakpoint breakpoint) {
    /**
     * Used from Popup Menu
     */
    class RemoveAction extends AnAction {
      private Breakpoint myBreakpoint;

      public RemoveAction(Breakpoint breakpoint) {
        super(DebuggerBundle.message("action.remove.text"));
        myBreakpoint = breakpoint;
      }

      public void actionPerformed(AnActionEvent e) {
        if (myBreakpoint != null) {
          removeBreakpoint(myBreakpoint);
          myBreakpoint = null;
        }
      }
    }

    /**
     * Used from Popup Menu
     */
    class SetEnabledAction extends AnAction {
      private boolean myNewValue;
      private Breakpoint myBreakpoint;

      public SetEnabledAction(Breakpoint breakpoint, boolean newValue) {
        super(newValue ? DebuggerBundle.message("action.enable.text") : DebuggerBundle.message("action.disable.text"));
        myBreakpoint = breakpoint;
        myNewValue = newValue;
      }

      public void actionPerformed(AnActionEvent e) {
        myBreakpoint.ENABLED = myNewValue;
        fireBreakpointChanged(myBreakpoint);
        myBreakpoint.updateUI();
      }
    }

      ViewBreakpointsAction viewBreakpointsAction = new ViewBreakpointsAction(DebuggerBundle.message("breakpoint.manager.action.view.breakpoints.text"));
      viewBreakpointsAction.setInitialBreakpoint(breakpoint);

      DefaultActionGroup group = new DefaultActionGroup();
      group.add(new SetEnabledAction(breakpoint, !breakpoint.ENABLED));
      group.add(new RemoveAction(breakpoint));
      group.addSeparator();
      group.add(viewBreakpointsAction);
      return group;
    }

  //interaction with RequestManagerImpl
  public void disableBreakpoints(final DebugProcessImpl debugProcess) {
    final List<Breakpoint> breakpoints = getBreakpoints();
    for (Iterator<Breakpoint> iterator = breakpoints.iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      debugProcess.getRequestsManager().deleteRequest(breakpoint);
    }
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        updateBreakpointsUI();
      }
    });
  }

  public void enableBreakpoints(final DebugProcessImpl debugProcess) {
    List<Breakpoint> breakpoints = getBreakpoints();
    for (Iterator<Breakpoint> iterator = breakpoints.iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      breakpoint.createRequest(debugProcess);
    }
  }

  public void updateBreakpoints(final DebugProcessImpl debugProcess) {
    List<Breakpoint> breakpoints = getBreakpoints();
    for (Iterator<Breakpoint> iterator = breakpoints.iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      RequestManagerImpl.updateRequests(breakpoint);
    }
  }

  public void updateAllRequests() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    List<Breakpoint> breakpoints = getBreakpoints();
    for (Iterator<Breakpoint> iterator = breakpoints.iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      fireBreakpointChanged(breakpoint);
    }
  }

  public void updateBreakpointsUI() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());
    for (Iterator<Breakpoint> iterator = getBreakpoints().iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      breakpoint.updateUI();
    }
  }

  public void reloadBreakpoints() {
    LOG.assertTrue(SwingUtilities.isEventDispatchThread());

    for (Iterator<Breakpoint> iterator = getBreakpoints().iterator(); iterator.hasNext();) {
      Breakpoint breakpoint = iterator.next();
      breakpoint.reload();
    }
  }

  public void addBreakpointManagerListener(BreakpointManagerListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removeBreakpointManagerListener(BreakpointManagerListener listener) {
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
        public void run() {
          myAlarm.addRequest(new Runnable() {
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

  public void setBreakpointEnabled(final Breakpoint breakpoint, final boolean enabled) {
    if (breakpoint.ENABLED != enabled) {
      breakpoint.ENABLED = enabled;
      fireBreakpointChanged(breakpoint);
      breakpoint.updateUI();
    }
  }
  
  public void addBreakpointRule(EnableBreakpointRule rule) {
    rule.init();
    myBreakpointRules.add(rule);
  }
  
  public boolean removeBreakpointRule(EnableBreakpointRule rule) {
    final boolean removed = myBreakpointRules.remove(rule);
    if (removed) {
      rule.dispose();
    }
    return removed;
  }
  
  public boolean removeBreakpointRule(@NotNull Breakpoint slaveBreakpoint) {
    for (Iterator<EnableBreakpointRule> it = myBreakpointRules.iterator(); it.hasNext();) {
      final EnableBreakpointRule rule = it.next();
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
    for (Iterator<EnableBreakpointRule> it = myBreakpointRules.iterator(); it.hasNext();) {
      final EnableBreakpointRule rule = it.next();
      rule.processBreakpointHit(breakpoint);
    }
  }

  public void setInitialBreakpointsState() {
    myAllowMulticasting = false;
    for (Iterator<EnableBreakpointRule> it = myBreakpointRules.iterator(); it.hasNext();) {
      it.next().init();
    }
    myAllowMulticasting = true;
    if (myBreakpointRules.size() > 0) {
      IJSwingUtilities.invoke(new Runnable() {
        public void run() {
          myDispatcher.getMulticaster().breakpointsChanged();
        }
      });
    }
  }
  
  public Breakpoint findMasterBreakpoint(@NotNull Breakpoint dependentBreakpoint) {
    for (Iterator<EnableBreakpointRule> it = myBreakpointRules.iterator(); it.hasNext();) {
      final EnableBreakpointRule rule = it.next();
      if (dependentBreakpoint.equals(rule.getSlaveBreakpoint())) {
        return rule.getMasterBreakpoint();
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
