package com.intellij.codeInspection.ui;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.deadCode.DeadCodeInspection;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefImplicitConstructor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.util.ResourceUtil;
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
  private InspectionResultsView myView;
  private HyperlinkListener myHyperLinkListener;
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
    myHTMLViewer.removeHyperlinkListener(myHyperLinkListener);
    myClickListeners.clear();
    myHTMLViewer = null;
  }

  public interface ClickListener {
    void referenceClicked(ClickEvent e);
  }

  private void showPageFromHistory(RefEntity newEntity) {
    InspectionTool tool = getTool(newEntity);
    try {
      if (!(newEntity instanceof RefElement) || tool instanceof DescriptorProviderInspection){
        showEmpty();
        return;
      }
      if (tool instanceof FilteringInspectionTool){
        if (tool instanceof DeadCodeInspection || ((FilteringInspectionTool)tool).getFilter().accepts((RefElement)newEntity)) {
          try {
            String html = generateHTML(newEntity, tool);
            myHTMLViewer.read(new StringReader(html), null);
            myHTMLViewer.setCaretPosition(0);
          }
          catch (Exception e) {
            showEmpty();
          }
        } else {
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
    if (newEntity instanceof RefImplicitConstructor) {
      newEntity = ((RefImplicitConstructor)newEntity).getOwnerClass();
    }

    //multiple problems for one entity -> refresh browser
    showPageFromHistory(newEntity);
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
                int descriptionIndex = Integer.parseInt(ref.substring("descr:".length()));
                CommonProblemDescriptor descriptor = ((DescriptorProviderInspection)getTool()).getDescriptions(
                  myCurrentEntity)[descriptionIndex];
                if (descriptor instanceof ProblemDescriptor) {
                  PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
                  if (psiElement == null) return;
                  VirtualFile vFile = psiElement.getContainingFile().getVirtualFile();
                  if (vFile != null) {
                    TextRange range = ((ProblemDescriptorImpl)descriptor).getTextRange();
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
                SuppressInspectionToolbarAction.getSuppressAction((RefElement)myCurrentEntity, getTool(), myCurrentDescriptor, myView).actionPerformed(null);
              }
              else {
                int offset = Integer.parseInt(ref);
                String fileURL = url.toExternalForm();
                fileURL = fileURL.substring(0, fileURL.indexOf('#'));
                VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(fileURL);
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

    add(new JScrollPane(myHTMLViewer), BorderLayout.CENTER);
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
      appendSuppressSection((RefElement)refEntity, buf, null);
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
      appendSuppressSection((RefElement)refEntity, buf, descriptor);
    }

    insertHeaderFooter(buf);
    return buf.toString();
  }

  private InspectionTool getTool(final RefEntity refEntity) {
    InspectionTool tool = getTool();
    assert tool != null;
    final GlobalInspectionContextImpl manager = tool.getContext();
    if (manager.RUN_WITH_EDITOR_PROFILE && refEntity instanceof RefElement){
      PsiElement element = ((RefElement)refEntity).getElement();
      if (element == null) return tool;
      tool = InspectionProjectProfileManager.getInstance(manager.getProject()).getProfileWrapper(element).getInspectionTool(tool.getShortName());
    }
    return tool;
  }

  private void appendSuppressSection(final RefElement refElement, final StringBuffer buf, final CommonProblemDescriptor descriptor) {
    final InspectionTool tool = getTool();
    if (tool != null) {
      final HighlightDisplayKey key = HighlightDisplayKey.find(tool.getShortName());
      if (key != null){//dummy entry points
        final AnAction suppressAction = SuppressInspectionToolbarAction.getSuppressAction(refElement, tool, descriptor, myView);
        if (suppressAction != null){
          @NonNls String font = "<font style=\"font-family:verdana;\" size = \"3\">";
          buf.append(font);
          @NonNls final String br = "<br>";
          buf.append(br).append(br);
          HTMLComposer.appendHeading(buf, InspectionsBundle.message("inspection.export.results.suppress"));
          buf.append(br);
          HTMLComposer.appendAfterHeaderIndention(buf);
          @NonNls final String href = "<a HREF=\"file://bred.txt#suppress:\">" + suppressAction.getTemplatePresentation().getText() + "</a>";
          buf.append(href);
          @NonNls String closeFont = "</font>";
          buf.append(closeFont);
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
    final URL descriptionUrl = getDescriptionUrl(tool);
    @NonNls final String underConstruction = "<b>" + UNDER_CONSTRUCTION + "</b></html>";
    try {
      if (descriptionUrl != null){
        @NonNls final String description = ResourceUtil.loadText(descriptionUrl);
        if (description != null && description.startsWith("<html>")) {
          page.append(description.substring(description.indexOf("<html>") + 6));
        } else {
          page.append(underConstruction);
        }
      } else {
        page.append(underConstruction);
      }
      page.append("</td></tr></table>");
      myHTMLViewer.setText(page.toString());
    }
    catch (IOException e) {
      try {
        myHTMLViewer.read(new StringReader(page.append(underConstruction).toString()), null);
      }
      catch (IOException e1) {
        //Can't be
      }
    } finally {
      myCurrentEntity = null;
    }
  }

  private static URL getDescriptionUrl(InspectionTool tool) {
    Class aClass = tool instanceof LocalInspectionToolWrapper ? ((LocalInspectionToolWrapper)tool).getTool().getClass() : tool.getClass();
    return ResourceUtil.getResource(aClass, "/inspectionDescriptions", tool.getDescriptionFileName());
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
      RefElement element = elementNode.getElement();
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
          if (!psiElement.isWritable()) {
            final ReadonlyStatusHandler.OperationStatus operationStatus = ReadonlyStatusHandler.getInstance(myView.getProject())
              .ensureFilesWritable(psiElement.getContainingFile().getVirtualFile());
            if (operationStatus.hasReadonlyFiles()) {
              return;
            }
          }

          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              Runnable command = new Runnable() {
                public void run() {
                  CommandProcessor.getInstance().markCurrentCommandAsComplex(myView.getProject());

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
                  CommandProcessor.getInstance().markCurrentCommandAsComplex(myView.getProject());

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

