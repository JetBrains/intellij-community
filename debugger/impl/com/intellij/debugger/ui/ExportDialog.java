/**
 * created at Dec 14, 2001
 * @author Jeka
 */
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerInvocationUtil;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.jdi.VirtualMachineProxyImpl;
import com.intellij.debugger.ui.impl.watch.MessageDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.sun.jdi.*;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Iterator;
import java.util.List;

public class ExportDialog extends DialogWrapper {
  private JTextArea myTextArea = new JTextArea();
  private TextFieldWithBrowseButton myTfFilePath;
  private Project myProject;
  private final DebugProcessImpl myDebugProcess;
  private CopyToClipboardAction myCopyToClipboardAction = new CopyToClipboardAction();
  private static final @NonNls String DEFAULT_REPORT_FILE_NAME = "threads_report.txt";

  public ExportDialog(DebugProcessImpl debugProcess, String destinationDirectory) {
    super(debugProcess.getProject(), true);
    myDebugProcess = debugProcess;
    myProject = debugProcess.getProject();
    setTitle(DebuggerBundle.message("threads.export.dialog.title"));
    setOKButtonText(DebuggerBundle.message("button.save"));

    init();

    setOKActionEnabled(false);
    myCopyToClipboardAction.setEnabled(false);

    myTextArea.setText(MessageDescriptor.EVALUATING.getLabel());
    debugProcess.getManagerThread().invoke(new ExportThreadsCommand(ApplicationManager.getApplication().getModalityStateForComponent(myTextArea)));

    myTfFilePath.setText(destinationDirectory + File.separator + DEFAULT_REPORT_FILE_NAME);
    setHorizontalStretch(1.5f);
  }

  protected Action[] createActions(){
    return new Action[]{getOKAction(), myCopyToClipboardAction, getCancelAction(), getHelpAction()};
  }

  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HelpID.EXPORT_THREADS);
  }

  protected JComponent createNorthPanel() {
    JPanel box = new JPanel(new BorderLayout());
    box.add(new JLabel(DebuggerBundle.message("label.threads.export.dialog.file")), BorderLayout.WEST);
    myTfFilePath = new TextFieldWithBrowseButton();
    myTfFilePath.addBrowseFolderListener(null, null, myProject, FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    box.add(myTfFilePath, BorderLayout.CENTER);
    JPanel panel = new JPanel(new BorderLayout());
    panel.add(box, BorderLayout.CENTER);
    panel.add(Box.createVerticalStrut(7), BorderLayout.SOUTH);
    return panel;
  }

  protected JComponent createCenterPanel() {
    myTextArea.setEditable(false);
    JScrollPane pane = new JScrollPane(myTextArea);
    pane.setPreferredSize(new Dimension(400, 300));
    return pane;
  }

  protected void doOKAction() {
    String path = myTfFilePath.getText();
    File file = new File(path);
    if (file.isDirectory()) {
      Messages.showMessageDialog(
        myProject,
        DebuggerBundle.message("error.threads.export.dialog.file.is.directory"),
        DebuggerBundle.message("threads.export.dialog.title"),
        Messages.getErrorIcon()
      );
    }
    else if (file.exists()) {
      int answer = Messages.showYesNoDialog(
        myProject,
        DebuggerBundle.message("error.threads.export.dialog.file.already.exists", path),
        DebuggerBundle.message("threads.export.dialog.title"),
        Messages.getQuestionIcon()
      );
      if (answer == 0) {
        super.doOKAction();
      }
    }
    else {
      super.doOKAction();
    }
  }

  public String getFilePath() {
    return myTfFilePath.getText();
  }

  public String getTextToSave() {
    return myTextArea.getText();
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.ExportDialog";
  }

  public String getExportThreadsText(VirtualMachineProxyImpl vmProxy) {
    StringBuffer buffer = new StringBuffer(512);
    List threads = vmProxy.getVirtualMachine().allThreads();
    for (Iterator it = threads.iterator(); it.hasNext();) {
      ThreadReference threadReference = (ThreadReference)it.next();
      buffer.append(threadName(threadReference));
      ReferenceType referenceType = threadReference.referenceType();
      if(referenceType != null) {
        //noinspection HardCodedStringLiteral
        Field daemon = referenceType.fieldByName("daemon");
        if(daemon != null) {
          Value value = threadReference.getValue(daemon);
          if(value instanceof BooleanValue && ((BooleanValue)value).booleanValue()) {
            buffer.append(" ").append(DebuggerBundle.message("threads.export.attribute.label.daemon"));
          }
        }

        //noinspection HardCodedStringLiteral
        Field priority = referenceType.fieldByName("priority");
        if(priority != null) {
          Value value = threadReference.getValue(priority);
          if(value instanceof IntegerValue) {
            buffer.append(", ").append(DebuggerBundle.message("threads.export.attribute.label.priority", ((IntegerValue) value).intValue()));
          }
        }
      }

      ThreadGroupReference groupReference = threadReference.threadGroup();
      if (groupReference != null) {
        buffer.append(", ").append(DebuggerBundle.message("threads.export.attribute.label.group", groupReference.name()));
      }
      buffer.append(", ").append(DebuggerBundle.message("threads.export.attribute.label.status", DebuggerUtilsEx.getThreadStatusText(threadReference.status())));

      try {
        if(vmProxy.canGetOwnedMonitorInfo() && vmProxy.canGetMonitorInfo()) {
          List list = threadReference.ownedMonitors();
          for (Iterator iterator = list.iterator(); iterator.hasNext();) {
            ObjectReference reference = (ObjectReference)iterator.next();
            List waiting = reference.waitingThreads();
            for (Iterator iterator1 = waiting.iterator(); iterator1.hasNext();) {
              ThreadReference thread = (ThreadReference)iterator1.next();
              buffer.append("\n\t ").append(DebuggerBundle.message("threads.export.attribute.label.blocks.thread", threadName(thread)));
            }
          }
        }

        ObjectReference waitedMonitor = vmProxy.canGetCurrentContendedMonitor() ? threadReference.currentContendedMonitor() : null;
        if(waitedMonitor != null) {
          if(vmProxy.canGetMonitorInfo()) {
            ThreadReference waitedThread = waitedMonitor.owningThread();
            if (waitedThread != null) {
              buffer.append("\n\t ").append(DebuggerBundle.message("threads.export.attribute.label.waiting.for.thread", threadName(waitedThread)));
            }
          }
        }

        List frames = threadReference.frames();
        for (Iterator frit = frames.iterator(); frit.hasNext();) {
          StackFrame stackFrame = (StackFrame)frit.next();
          final Location location = stackFrame.location();
          buffer.append("\n\t  ").append(renderLocation(location));
        }
      }
      catch (IncompatibleThreadStateException e) {
        buffer.append("\n\t ").append(DebuggerBundle.message("threads.export.attribute.error.incompatible.state"));
      }
      buffer.append("\n\n");
    }
    return buffer.toString();
  }

  private String renderLocation(final Location location) {
    String sourceName;
    try {
      sourceName = location.sourceName();
    }
    catch (AbsentInformationException e) {
      sourceName = "Unknown Source";
    }
    return DebuggerBundle.message(
        "export.threads.stackframe.format",
        location.declaringType().name() + "." + location.method().name(), 
        sourceName, 
        location.lineNumber()
    );
  }

  private String threadName(ThreadReference threadReference) {
    return threadReference.name() + "@" + Long.toHexString(threadReference.uniqueID());
  }

  private class CopyToClipboardAction extends AbstractAction {
    public CopyToClipboardAction() {
      super(DebuggerBundle.message("button.copy"));
      putValue(Action.SHORT_DESCRIPTION, DebuggerBundle.message("export.dialog.copy.action.description"));
    }

    public void actionPerformed(ActionEvent e) {
      String s = StringUtil.convertLineSeparators(myTextArea.getText(), "\n");
      CopyPasteManager.getInstance().setContents(new StringSelection(s));
    }
  }

  private class ExportThreadsCommand extends DebuggerCommandImpl {
    protected ModalityState myModalityState;

    public ExportThreadsCommand(ModalityState modalityState) {
      myModalityState = modalityState;
    }

    private void setText(final String text) {
      DebuggerInvocationUtil.invokeLater(myProject, new Runnable() {
          public void run() {
            myTextArea.setText(text);
            setOKActionEnabled(true);
            myCopyToClipboardAction.setEnabled(true);
          }
        }, myModalityState);
    }

    protected void action() {
      setText(getExportThreadsText(myDebugProcess.getVirtualMachineProxy()));
    }
  }
}
