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
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.DebugProcessEvents;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.*;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.breakpoints.*;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.StringBuilderSpinAllocator;
import com.intellij.xdebugger.impl.actions.ViewBreakpointsAction;
import com.intellij.xdebugger.ui.DebuggerColors;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.event.MethodEntryEvent;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jul 9, 2003
 * Time: 6:24:35 PM
 * To change this template use Options | File Templates.
 */
public class PositionHighlighter {
  private static final Key<Boolean> HIGHLIGHTER_USERDATA_KEY = new Key<Boolean>("HIGHLIGHTER_USERDATA_KEY");
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.ui.PositionHighlighter");
  private final Project myProject;
  private DebuggerContextImpl myContext = DebuggerContextImpl.EMPTY_CONTEXT;
  private SelectionDescription      mySelectionDescription = null;
  private ExecutionPointDescription myExecutionPointDescription = null;

  public PositionHighlighter(Project project, DebuggerStateManager stateManager) {
    myProject = project;

    stateManager.addListener(new DebuggerContextListener() {
      public void changeEvent(DebuggerContextImpl newContext, int event) {
        myContext = newContext;
        if (event != DebuggerSession.EVENT_REFRESH_VIEWS_ONLY && event != DebuggerSession.EVENT_THREADS_REFRESH) {
          refresh();
        }
      }
    });
  }

  private void showLocationInEditor() {
    myContext.getDebugProcess().getManagerThread().schedule(new ShowLocationCommand(myContext));
  }

  private void refresh() {
    clearSelections();
    final DebuggerSession session = myContext.getDebuggerSession();
    if(session != null) {
      switch(session.getState()) {
        case DebuggerSession.STATE_PAUSED:
          if(myContext.getFrameProxy() != null) {
            showLocationInEditor();
            return;
          }
          break;
      }
    }
  }

  protected static class ExecutionPointDescription extends SelectionDescription {
    private RangeHighlighter myHighlighter;
    private final int myLineIndex;

    protected ExecutionPointDescription(Editor editor, int lineIndex) {
      super(editor);
      myLineIndex = lineIndex;
    }

    public void select() {
      if(myIsActive) return;
      myIsActive = true;
      EditorColorsScheme scheme = EditorColorsManager.getInstance().getGlobalScheme();
      myHighlighter = myEditor.getMarkupModel().addLineHighlighter(
        myLineIndex,
        DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
        scheme.getAttributes(DebuggerColors.EXECUTIONPOINT_ATTRIBUTES)
      );
      myHighlighter.setErrorStripeTooltip(DebuggerBundle.message("position.highlighter.stripe.tooltip"));
      myHighlighter.putUserData(HIGHLIGHTER_USERDATA_KEY, Boolean.TRUE);
    }

    public void remove() {
      if(!myIsActive) return;
      myIsActive = false;
      if (myHighlighter != null) {
        myEditor.getMarkupModel().removeHighlighter(myHighlighter);
        myHighlighter = null;
      }
    }

    public RangeHighlighter getHighlighter() {
      return myHighlighter;
    }
  }

  protected abstract static class SelectionDescription {
      protected Editor myEditor;
      protected boolean myIsActive;

      public SelectionDescription(Editor editor) {
        myEditor = editor;
      }

      public abstract void select();
      public abstract void remove();

      public static ExecutionPointDescription createExecutionPoint(final Editor editor,
                                                                   final int lineIndex) {
        return new ExecutionPointDescription(editor, lineIndex);
      }

      public static SelectionDescription createSelection(final Editor editor, final int lineIndex) {
        return new SelectionDescription(editor) {
          public void select() {
            if(myIsActive) return;
            myIsActive = true;
            DocumentEx doc = (DocumentEx)editor.getDocument();
            editor.getSelectionModel().setSelection(
              doc.getLineStartOffset(lineIndex),
              doc.getLineEndOffset(lineIndex) + doc.getLineSeparatorLength(lineIndex)
            );
          }

          public void remove() {
            if(!myIsActive) return;
            myIsActive = false;
            myEditor.getSelectionModel().removeSelection();
          }
        };
      }
    }

  private void showSelection(SourcePosition position) {
    Editor editor = getEditor(position);
    if(editor == null) {
      return;
    }
    if (mySelectionDescription != null) {
      mySelectionDescription.remove();
    }
    mySelectionDescription = SelectionDescription.createSelection(editor, position.getLine());
    mySelectionDescription.select();
  }

  private void showExecutionPoint(final SourcePosition position, List<Pair<Breakpoint, Event>> events) {
    if (myExecutionPointDescription != null) {
      myExecutionPointDescription.remove();
    }
    int lineIndex = position.getLine();
    Editor editor = getEditor(position);
    if(editor == null) {
      return;
    }
    myExecutionPointDescription = SelectionDescription.createExecutionPoint(editor, lineIndex);
    myExecutionPointDescription.select();

    RangeHighlighter highlighter = myExecutionPointDescription.getHighlighter();

    if(highlighter != null) {
      final List<Pair<Breakpoint, Event>> eventsOutOfLine = new ArrayList<Pair<Breakpoint, Event>>();

      for (Iterator<Pair<Breakpoint, Event>> iterator = events.iterator(); iterator.hasNext();) {
        final Pair<Breakpoint, Event> eventDescriptor = iterator.next();
        final Breakpoint breakpoint = eventDescriptor.getFirst();
        // filter breakpoints that do not match the event
        if (breakpoint instanceof MethodBreakpoint) {
          try {
            if (!((MethodBreakpoint)breakpoint).matchesEvent((LocatableEvent)eventDescriptor.getSecond(), myContext.getDebugProcess())) {
              continue;
            }
          }
          catch (EvaluateException ignored) {
          }
        }
        else if (breakpoint instanceof WildcardMethodBreakpoint) {
          if (!((WildcardMethodBreakpoint)breakpoint).matchesEvent((LocatableEvent)eventDescriptor.getSecond())) {
            continue;
          }
        }

        if(breakpoint instanceof BreakpointWithHighlighter) {
          breakpoint.reload();
          final SourcePosition sourcePosition = ((BreakpointWithHighlighter)breakpoint).getSourcePosition();
          if(sourcePosition == null || sourcePosition.getLine() != lineIndex) {
            eventsOutOfLine.add(eventDescriptor);
          }
        }
        else {
          eventsOutOfLine.add(eventDescriptor);
        }
      }

      if(eventsOutOfLine.size() > 0) {
        highlighter.setGutterIconRenderer(new GutterIconRenderer() {
          @NotNull
          public Icon getIcon() {
            return eventsOutOfLine.get(0).getFirst().getIcon();
          }

          public String getTooltipText() {
            DebugProcessImpl debugProcess = myContext.getDebugProcess();
            if(debugProcess != null) {
              final StringBuilder buf = StringBuilderSpinAllocator.alloc();
              try {
                //noinspection HardCodedStringLiteral
                buf.append("<html><body>");
                for (Iterator<Pair<Breakpoint, Event>> iterator = eventsOutOfLine.iterator(); iterator.hasNext();) {
                  Pair<Breakpoint, Event> eventDescriptor = iterator.next();
                  buf.append(((DebugProcessEvents)debugProcess).getEventText(eventDescriptor));
                  if(iterator.hasNext()) {
                    //noinspection HardCodedStringLiteral
                    buf.append("<br>");
                  }
                }
                //noinspection HardCodedStringLiteral
                buf.append("</body></html>");
                return buf.toString();
              }
              finally {
                StringBuilderSpinAllocator.dispose(buf);
              }
            }
            else {
              return null;
            }
          }

          public ActionGroup getPopupMenuActions() {
            DefaultActionGroup group = new DefaultActionGroup();
            for (Iterator<Pair<Breakpoint, Event>> iterator = eventsOutOfLine.iterator(); iterator.hasNext();) {
              Pair<Breakpoint, Event> eventDescriptor = iterator.next();
              Breakpoint breakpoint = eventDescriptor.getFirst();
              ViewBreakpointsAction viewBreakpointsAction = new ViewBreakpointsAction(breakpoint.getDisplayName(), breakpoint);
              group.add(viewBreakpointsAction);
            }

            return group;
          }
        });
      }
    }
  }

  private Editor getEditor(SourcePosition position) {
    final PsiFile psiFile = position.getFile();
    Document doc = PsiDocumentManager.getInstance(myProject).getDocument(psiFile);
    if (!psiFile.isValid()) {
      return null;
    }
    final int lineIndex = position.getLine();
    if (lineIndex < 0 || lineIndex > doc.getLineCount()) {
      //LOG.assertTrue(false, "Incorrect lineIndex " + lineIndex + " in file " + psiFile.getName());
      return null;
    }
    return position.openEditor(false);
  }

  private void clearSelections() {
    if (mySelectionDescription != null || myExecutionPointDescription != null) {
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        public void run() {
          if (mySelectionDescription != null) {
            mySelectionDescription.remove();
            mySelectionDescription = null;
          }
          if (myExecutionPointDescription != null) {
            myExecutionPointDescription.remove();
            myExecutionPointDescription = null;
          }
        }
      });
    }
  }


  public void updateContextPointDescription() {
    if(myContext.getDebuggerSession() == null) return;

    showLocationInEditor();
  }

  private class ShowLocationCommand extends DebuggerContextCommandImpl {
    private final DebuggerContextImpl myContext;

    public ShowLocationCommand(DebuggerContextImpl context) {
      super(context);
      myContext = context;
    }

    public void threadAction() {
      final SourcePosition contextPosition = myContext.getSourcePosition();
      if (contextPosition == null) {
        return;
      }
      
      boolean isExecutionPoint = false;
      try {
        StackFrameProxyImpl frameProxy = myContext.getFrameProxy();
        final ThreadReferenceProxyImpl thread = getSuspendContext().getThread();
        isExecutionPoint = thread != null && frameProxy.equals(thread.frame(0));
      }
      catch(Throwable th) {
        LOG.debug(th);
      }

      final List<Pair<Breakpoint, Event>> events = DebuggerUtilsEx.getEventDescriptors(getSuspendContext());

      final SourcePosition position = ApplicationManager.getApplication().runReadAction(new Computable<SourcePosition>() {
        public SourcePosition compute() {
          Document document = PsiDocumentManager.getInstance(myProject).getDocument(contextPosition.getFile());
          if(document != null) {
            if(contextPosition.getLine() < 0 || contextPosition.getLine() >= document.getLineCount()) {
              return SourcePosition.createFromLine(contextPosition.getFile(), 0);
            }
          }
          return contextPosition;
        }
      });

      if(isExecutionPoint) {
        DebuggerInvocationUtil.swingInvokeLater(myProject, new Runnable() {
          public void run() {
            final SourcePosition highlightPosition = getHighlightPosition(events, position);
            showExecutionPoint(highlightPosition, events);
          }
        });
      }
      else {
        DebuggerInvocationUtil.swingInvokeLater(myProject, new Runnable() {
          public void run() {
            showSelection(position);
          }
        });
      }
    }

    private SourcePosition getHighlightPosition(final List<Pair<Breakpoint, Event>> events, SourcePosition position) {
      for (Iterator<Pair<Breakpoint, Event>> iterator = events.iterator(); iterator.hasNext();) {
        final Pair<Breakpoint, Event> eventDescriptor = iterator.next();
        final Breakpoint breakpoint = eventDescriptor.getFirst();
        if(breakpoint instanceof LineBreakpoint) {
          breakpoint.reload();
          final SourcePosition breakPosition = ((BreakpointWithHighlighter)breakpoint).getSourcePosition();
          if(breakPosition != null && breakPosition.getLine() != position.getLine()) {
            position = SourcePosition.createFromLine(position.getFile(), breakPosition.getLine());
          }
        }
        else if(breakpoint instanceof MethodBreakpoint) {
          final MethodBreakpoint methodBreakpoint = (MethodBreakpoint)breakpoint;
          methodBreakpoint.reload();
          final SourcePosition breakPosition = methodBreakpoint.getSourcePosition();
          final LocatableEvent event = (LocatableEvent)eventDescriptor.getSecond();
          if(breakPosition != null && breakPosition.getFile().equals(position.getFile()) && breakPosition.getLine() != position.getLine() && event instanceof MethodEntryEvent) {
            try {
              if (methodBreakpoint.matchesEvent(event, myContext.getDebugProcess())) {
                position = SourcePosition.createFromLine(position.getFile(), breakPosition.getLine());
              }
            }
            catch (EvaluateException ignored) {
            }
          }
        }
      }
      return position;
    }
  }

}
