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

package com.intellij.codeInspection.ui;

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.actions.SuppressActionWrapper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLFrameHyperlinkEvent;
import java.awt.*;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Browser extends JPanel {
  private static final String UNDER_CONSTRUCTION = InspectionsBundle.message("inspection.tool.description.under.construction.text");
  private final List<ClickListener> myClickListeners;
  private RefEntity myCurrentEntity;
  private JEditorPane myHTMLViewer;
  private final InspectionResultsView myView;
  private final HyperlinkListener myHyperLinkListener;
  private CommonProblemDescriptor myCurrentDescriptor;

  public static class ClickEvent {
    public static final int REF_ELEMENT = 1;
    public static final int FILE_OFFSET = 2;
    private final VirtualFile myFile;
    private final int myStartPosition;
    private final int myEndPosition;
    private final RefElement refElement;
    private final int myEventType;

    public ClickEvent(VirtualFile myFile, int myStartPosition, int myEndPosition) {
      this.myFile = myFile;
      this.myStartPosition = myStartPosition;
      this.myEndPosition = myEndPosition;
      myEventType = FILE_OFFSET;
      refElement = null;
    }

    public int getEventType() {
      return myEventType;
    }

    public VirtualFile getFile() {
      return myFile;
    }

    public int getStartOffset() {
      return myStartPosition;
    }

    public int getEndOffset() {
      return myEndPosition;
    }

    public RefElement getClickedElement() {
      return refElement;
    }

  }

  public void dispose(){
    removeAll();
    if (myHTMLViewer != null) {
      myHTMLViewer.removeHyperlinkListener(myHyperLinkListener);
      myHTMLViewer = null;
    }
    myClickListeners.clear();
  }

  public interface ClickListener {
    void referenceClicked(ClickEvent e);
  }

  private void showPageFromHistory(RefEntity newEntity) {
    InspectionTool tool = getTool(newEntity);
    try {
      if (tool instanceof DescriptorProviderInspection) {
        showEmpty();
      }
      else {
        try {
          String html = generateHTML(newEntity, tool);
          myHTMLViewer.read(new StringReader(html), null);
          myHTMLViewer.setCaretPosition(0);
        }
        catch (Exception e) {
          showEmpty();
        }
      }
    }
    finally {
      myCurrentEntity = newEntity;
      myCurrentDescriptor = null;
    }
  }

  public void showPageFor(RefEntity refEntity, CommonProblemDescriptor descriptor) {
    try {
      String html = generateHTML(refEntity, descriptor);
      myHTMLViewer.read(new StringReader(html), null);
      myHTMLViewer.setCaretPosition(0);
    }
    catch (Exception e) {
      showEmpty();
    }
    finally {
      myCurrentEntity = refEntity;
      myCurrentDescriptor = descriptor;
    }
  }

  public void showPageFor(RefEntity newEntity) {
    if (newEntity == null) {
      showEmpty();
      return;
    }
    //multiple problems for one entity -> refresh browser
    showPageFromHistory(newEntity.getRefManager().getRefinedElement(newEntity));
  }

  public Browser(InspectionResultsView view) {
    super(new BorderLayout());
    myView = view;

    myClickListeners = new ArrayList<ClickListener>();
    myCurrentEntity = null;
    myCurrentDescriptor = null;

    myHTMLViewer = new JEditorPane(UIUtil.HTML_MIME, InspectionsBundle.message("inspection.offline.view.empty.browser.text"));
    myHTMLViewer.setEditable(false);
    myHyperLinkListener = new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          JEditorPane pane = (JEditorPane)e.getSource();
          if (e instanceof HTMLFrameHyperlinkEvent) {
            HTMLFrameHyperlinkEvent evt = (HTMLFrameHyperlinkEvent)e;
            HTMLDocument doc = (HTMLDocument)pane.getDocument();
            doc.processHTMLFrameHyperlinkEvent(evt);
          }
          else {
            try {
              URL url = e.getURL();
              @NonNls String ref = url.getRef();
              if (ref.startsWith("pos:")) {
                int delimeterPos = ref.indexOf(':', "pos:".length() + 1);
                String startPosition = ref.substring("pos:".length(), delimeterPos);
                String endPosition = ref.substring(delimeterPos + 1);
                Integer textStartOffset = new Integer(startPosition);
                Integer textEndOffset = new Integer(endPosition);
                String fileURL = url.toExternalForm();
                fileURL = fileURL.substring(0, fileURL.indexOf('#'));
                VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(fileURL);
                if (vFile != null) {
                  fireClickEvent(vFile, textStartOffset.intValue(), textEndOffset.intValue());
                }
              }
              else if (ref.startsWith("descr:")) {
                if (myCurrentDescriptor instanceof ProblemDescriptor) {
                  PsiElement psiElement = ((ProblemDescriptor)myCurrentDescriptor).getPsiElement();
                  if (psiElement == null) return;
                  VirtualFile vFile = psiElement.getContainingFile().getVirtualFile();
                  if (vFile != null) {
                    TextRange range = ((ProblemDescriptorImpl)myCurrentDescriptor).getTextRange();
                    fireClickEvent(vFile, range.getStartOffset(), range.getEndOffset());
                  }
                }
              }
              else if (ref.startsWith("invoke:")) {
                int actionNumber = Integer.parseInt(ref.substring("invoke:".length()));
                getTool().getQuickFixes(new RefElement[]{(RefElement)myCurrentEntity})[actionNumber]
                  .doApplyFix(new RefElement[]{(RefElement)myCurrentEntity}, myView);
              }
              else if (ref.startsWith("invokelocal:")) {
                int actionNumber = Integer.parseInt(ref.substring("invokelocal:".length()));
                if (actionNumber > -1) {
                  invokeLocalFix(actionNumber);
                }
              } else if (ref.startsWith("suppress:")){
                final SuppressActionWrapper.SuppressTreeAction[] suppressTreeActions =
                  new SuppressActionWrapper(myView.getProject(), getTool(), myView.getTree().getSelectionPaths()).getChildren(null);
                final List<AnAction> activeActions = new ArrayList<AnAction>();
                for (SuppressActionWrapper.SuppressTreeAction suppressTreeAction : suppressTreeActions) {
                  if (suppressTreeAction.isAvailable()) activeActions.add(suppressTreeAction);
                }
                if (!activeActions.isEmpty()) {
                  int actionNumber = Integer.parseInt(ref.substring("suppress:".length()));
                  if (actionNumber > -1 && activeActions.size() > actionNumber) {
                    activeActions.get(actionNumber).actionPerformed(null);
                  }
                }
              }
              else {
                int offset = Integer.parseInt(ref);
                String fileURL = url.toExternalForm();
                fileURL = fileURL.substring(0, fileURL.indexOf('#'));
                VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(fileURL);
                if (vFile == null) {
                  vFile = VfsUtil.findFileByURL(url);
                }
                if (vFile != null) {
                  fireClickEvent(vFile, offset, offset);
                }
              }
            }
            catch (Throwable t) {
              //???
            }
          }
        }
      }
    };
    myHTMLViewer.addHyperlinkListener(myHyperLinkListener);

    add(new JBScrollPane(myHTMLViewer), BorderLayout.CENTER);
  }

  public void addClickListener(ClickListener listener) {
    myClickListeners.add(listener);
  }

  private void fireClickEvent(VirtualFile file, int startPosition, int endPosition) {
    ClickEvent e = new ClickEvent(file, startPosition, endPosition);

    for (ClickListener listener : myClickListeners) {
      listener.referenceClicked(e);
    }
  }

  private String generateHTML(final RefEntity refEntity, final InspectionTool tool) {
    final StringBuffer buf = new StringBuffer();
    if (refEntity instanceof RefElement) {
      final Runnable action = new Runnable() {
        public void run() {
          tool.getComposer().compose(buf, refEntity);
        }
      };
      ApplicationManager.getApplication().runReadAction(action);
    }
    else {
      tool.getComposer().compose(buf, refEntity);
    }

    uppercaseFirstLetter(buf);

    if (refEntity instanceof RefElement){
      appendSuppressSection(buf);
    }

    insertHeaderFooter(buf);

    return buf.toString();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private static void insertHeaderFooter(final StringBuffer buf) {
    buf.insert(0, "<HTML><BODY><font style=\"font-family:verdana;\" size = \"3\">");
    buf.append("</font></BODY></HTML>");
  }

  private String generateHTML(final RefEntity refEntity, final CommonProblemDescriptor descriptor) {
    final StringBuffer buf = new StringBuffer();
    final Runnable action = new Runnable() {
      public void run() {
        InspectionTool tool = getTool(refEntity);
        tool.getComposer().compose(buf, refEntity, descriptor);
      }
    };
    ApplicationManager.getApplication().runReadAction(action);

    uppercaseFirstLetter(buf);

    if (refEntity instanceof RefElement) {
      appendSuppressSection(buf);
    }

    insertHeaderFooter(buf);
    return buf.toString();
  }

  private InspectionTool getTool(final RefEntity refEntity) {
    InspectionTool tool = getTool();
    assert tool != null;
    final GlobalInspectionContextImpl manager = tool.getContext();
    if (true && refEntity instanceof RefElement){
      PsiElement element = ((RefElement)refEntity).getElement();
      if (element == null) return tool;
      tool = InspectionProjectProfileManager.getInstance(manager.getProject()).getProfileWrapper().getInspectionTool(tool.getShortName(),
                                                                                                                            element);
    }
    return tool;
  }

  private void appendSuppressSection(final StringBuffer buf) {
    final InspectionTool tool = getTool();
    if (tool != null) {
      final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
      if (key != null){//dummy entry points
        final SuppressActionWrapper.SuppressTreeAction[] suppressActions = new SuppressActionWrapper(myView.getProject(), tool, myView.getTree().getSelectionPaths()).getChildren(null);
        if (suppressActions.length > 0) {
          final List<AnAction> activeSuppressActions = new ArrayList<AnAction>();
          for (SuppressActionWrapper.SuppressTreeAction suppressAction : suppressActions) {
            if (suppressAction.isAvailable()) {
              activeSuppressActions.add(suppressAction);
            }
          }
          if (!activeSuppressActions.isEmpty()) {
            int idx = 0;
            @NonNls String font = "<font style=\"font-family:verdana;\" size = \"3\">";
            buf.append(font);
            @NonNls final String br = "<br>";
            buf.append(br).append(br);
            HTMLComposerImpl.appendHeading(buf, InspectionsBundle.message("inspection.export.results.suppress"));
            for (AnAction suppressAction : activeSuppressActions) {
              buf.append(br);
              HTMLComposer.appendAfterHeaderIndention(buf);
              @NonNls final String href = "<a HREF=\"file://bred.txt#suppress:" + idx + "\">" + suppressAction.getTemplatePresentation().getText() + "</a>";
              buf.append(href);
              idx++;
            }
            @NonNls String closeFont = "</font>";
            buf.append(closeFont);
          }
        }
      }
    }
  }

  private static void uppercaseFirstLetter(final StringBuffer buf) {
    if (buf.length() > 1) {
      char[] firstLetter = new char[1];
      buf.getChars(0, 1, firstLetter, 0);
      buf.setCharAt(0, Character.toUpperCase(firstLetter[0]));
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public void showEmpty() {
    myCurrentEntity = null;
    try {
      myHTMLViewer.read(new StringReader("<html><body></body></html>"), null);
    }
    catch (IOException e) {
      //can't be
    }
  }

  public void showDescription(InspectionTool tool){
    if (tool.getShortName().length() == 0){
      showEmpty();
      return;
    }
    @NonNls StringBuffer page = new StringBuffer("<html>");
    page.append("<table border='0' cellspacing='0' cellpadding='0' width='100%'>");
    page.append("<tr><td colspan='2'>");
    HTMLComposer.appendHeading(page, InspectionsBundle.message("inspection.tool.in.browser.id.title"));
    page.append("</td></tr>");
    page.append("<tr><td width='37'></td>" +
                "<td>");
    page.append(tool.getShortName());
    page.append("</td></tr>");
    page.append("<tr height='10'></tr>");
    page.append("<tr><td colspan='2'>");
    HTMLComposer.appendHeading(page, InspectionsBundle.message("inspection.tool.in.browser.description.title"));
    page.append("</td></tr>");
    page.append("<tr><td width='37'></td>" +
                "<td>");
    @NonNls final String underConstruction = "<b>" + UNDER_CONSTRUCTION + "</b></html>";
    try {
      @NonNls String description = tool.loadDescription();
      if (description == null) {
        description = underConstruction;
      }
      if (description.startsWith("<html>")) {
        page.append(description.substring(description.indexOf("<html>") + 6));
      } else {
        page.append(underConstruction);
      }

      page.append("</td></tr></table>");
      myHTMLViewer.setText(page.toString());
    } finally {
      myCurrentEntity = null;
    }
  }

  @Nullable
  private InspectionTool getTool() {
    if (myView != null){
      return myView.getTree().getSelectedTool();
    }
    return null;
  }

  public void invokeLocalFix(int idx) {
    if (myView.getTree().getSelectionCount() != 1) return;
    final InspectionTreeNode node = (InspectionTreeNode)myView.getTree().getSelectionPath().getLastPathComponent();
    if (node instanceof ProblemDescriptionNode) {
      final ProblemDescriptionNode problemNode = (ProblemDescriptionNode)node;
      final CommonProblemDescriptor descriptor = problemNode.getDescriptor();
      final RefEntity element = problemNode.getElement();
      invokeFix(element, descriptor, idx);
    }
    else if (node instanceof RefElementNode) {
      RefElementNode elementNode = (RefElementNode)node;
      RefEntity element = elementNode.getElement();
      CommonProblemDescriptor descriptor = elementNode.getProblem();
      if (descriptor != null) {
        invokeFix(element, descriptor, idx);
      }
    }
  }

  private void invokeFix(final RefEntity element, final CommonProblemDescriptor descriptor, final int idx) {
    final QuickFix[] fixes = descriptor.getFixes();
    if (fixes != null && fixes.length > idx && fixes[idx] != null) {
      if (element instanceof RefElement) {
        PsiElement psiElement = ((RefElement)element).getElement();
        if (psiElement != null && psiElement.isValid()) {
          if (!CodeInsightUtilBase.preparePsiElementForWrite(psiElement)) return;

          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              Runnable command = new Runnable() {
                public void run() {
                  CommandProcessor.getInstance().markCurrentCommandAsGlobal(myView.getProject());

                  //CCE here means QuickFix was incorrectly inherited
                  fixes[idx].applyFix(myView.getProject(), descriptor);
                }
              };
              CommandProcessor.getInstance().executeCommand(myView.getProject(), command, fixes[idx].getName(), null);
              final DescriptorProviderInspection tool = ((DescriptorProviderInspection)myView.getTree().getSelectedTool());
              if (tool != null) {
                tool.ignoreProblem(element, descriptor, idx);
              }
              myView.updateView(false);
            }
          });
        }
      } else {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              Runnable command = new Runnable() {
                public void run() {
                  CommandProcessor.getInstance().markCurrentCommandAsGlobal(myView.getProject());

                  //CCE here means QuickFix was incorrectly inherited
                  fixes[idx].applyFix(myView.getProject(), descriptor);
                }
              };
              CommandProcessor.getInstance().executeCommand(myView.getProject(), command, fixes[idx].getName(), null);
              final DescriptorProviderInspection tool = ((DescriptorProviderInspection)myView.getTree().getSelectedTool());
              if (tool != null) {
                tool.ignoreProblem(element, descriptor, idx);
              }
              myView.updateView(false);
            }
          });
      }
    }
  }
}

