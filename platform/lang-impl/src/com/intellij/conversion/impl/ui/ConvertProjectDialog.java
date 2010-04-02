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

package com.intellij.conversion.impl.ui;

import com.intellij.conversion.impl.ConversionContextImpl;
import com.intellij.conversion.impl.ConversionRunner;
import com.intellij.conversion.impl.ProjectConversionUtil;
import com.intellij.conversion.CannotConvertException;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.HashSet;
import com.intellij.CommonBundle;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
public class ConvertProjectDialog extends DialogWrapper {
  private static final Logger LOG = Logger.getInstance("#com.intellij.conversion.impl.ui.ConvertProjectDialog");
  private JPanel myMainPanel;
  private JTextPane myTextPane;
  private boolean myConverted;
  private final ConversionContextImpl myContext;
  private final List<ConversionRunner> myConversionRunners;
  private final File myBackupDir;
  private final Set<File> myAffectedFiles;
  private boolean myNonExistingFilesMessageShown;

  public ConvertProjectDialog(ConversionContextImpl context, final List<ConversionRunner> conversionRunners) {
    super(true);
    setTitle(IdeBundle.message("dialog.title.convert.project"));
    setModal(true);
    myContext = context;
    myConversionRunners = conversionRunners;
    myAffectedFiles = new HashSet<File>();
    for (ConversionRunner conversionRunner : conversionRunners) {
      myAffectedFiles.addAll(conversionRunner.getAffectedFiles());
    }

    myBackupDir = ProjectConversionUtil.getBackupDir(context.getProjectBaseDir());
    myTextPane.setFont(UIUtil.getLabelFont());
    myTextPane.setContentType("text/html");
    final HTMLEditorKit editorKit = new HTMLEditorKit();
    editorKit.getStyleSheet().addRule(UIUtil.displayPropertiesToCSS(UIUtil.getLabelFont(), UIUtil.getLabelForeground()));
    myTextPane.setEditorKit(editorKit);
    myTextPane.setEditable(false);
    myTextPane.setBackground(UIUtil.getLabelBackground());
    myTextPane.setForeground(UIUtil.getLabelForeground());
    myTextPane.setText(IdeBundle.message("label.text.project.has.older.format", context.getProjectFile().getName(), myBackupDir.getAbsolutePath()));

    myTextPane.addHyperlinkListener(new HyperlinkListener() {
      public void hyperlinkUpdate(HyperlinkEvent e) {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          @NonNls StringBuilder descriptions = new StringBuilder("<html>The following conversions will be performed:<br>");
          for (ConversionRunner runner : conversionRunners) {
            descriptions.append(runner.getProvider().getConversionDescription()).append("<br>");
          }
          descriptions.append("</html>");
          Messages.showInfoMessage(descriptions.toString(), IdeBundle.message("dialog.title.convert.project"));
        }
      }
    });
    init();
    setOKButtonText("Convert");
  }

  @Override
  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  protected void doOKAction() {
    final List<File> nonexistentFiles = myContext.getNonExistingModuleFiles();
    if (!nonexistentFiles.isEmpty() && !myNonExistingFilesMessageShown) {
      final String filesString = getFilesString(nonexistentFiles);
      final int res = Messages.showYesNoDialog(getContentPane(), IdeBundle.message("message.files.doesn.t.exists.0.so.the.corresponding.modules.won.t.be.converted.do.you.want.to.continue",
                                                                                   filesString),
                                                                 IdeBundle.message("dialog.title.convert.project"),
                                                                 Messages.getQuestionIcon());
      if (res != 0) {
        super.doOKAction();
        return;
      }
      myNonExistingFilesMessageShown = false;
    }


    try {
      if (!checkReadOnlyFiles()) {
        return;
      }

      ProjectConversionUtil.backupFiles(myAffectedFiles, myContext.getProjectBaseDir(), myBackupDir);
      for (ConversionRunner runner : myConversionRunners) {
        if (runner.isConversionNeeded()) {
          runner.preProcess();
          runner.process();
          runner.postProcess();
        }
      }
      myContext.saveFiles(myAffectedFiles);
      myConverted = true;
      super.doOKAction();
    }
    catch (CannotConvertException e) {
      LOG.info(e);
      showErrorMessage(IdeBundle.message("error.cannot.convert.project", e.getMessage()));
    }
    catch (IOException e) {
      LOG.info(e);
      showErrorMessage(IdeBundle.message("error.cannot.convert.project", e.getMessage()));
    }
  }

  private static String getFilesString(List<File> files) {
    StringBuilder buffer = new StringBuilder();
    for (File file : files) {
      buffer.append(file.getAbsolutePath()).append("<br>");
    }
    return buffer.toString();
  }

  private boolean checkReadOnlyFiles() throws IOException {
    List<File> files = getReadOnlyFiles();
    if (!files.isEmpty()) {
      final String message = IdeBundle.message("message.text.unlock.read.only.files", getFilesString(files));
      final String[] options = {CommonBundle.getContinueButtonText(), CommonBundle.getCancelButtonText()};
      if (Messages.showDialog(myMainPanel, message, IdeBundle.message("dialog.title.convert.project"), options, 0, null) != 0) {
        return false;
      }
      unlockFiles(files);

      files = getReadOnlyFiles();
      if (!files.isEmpty()) {
        showErrorMessage(IdeBundle.message("error.message.cannot.make.files.writable", getFilesString(files)));
        return false;
      }
    }
    return true;
  }

  private List<File> getReadOnlyFiles() {
    return ConversionRunner.getReadOnlyFiles(myAffectedFiles);
  }

  private static void unlockFiles(final List<File> files) throws IOException {
    for (File file : files) {
      FileUtil.setReadOnlyAttribute(file.getAbsolutePath(), false);
    }
  }

  private void showErrorMessage(final String message) {
    Messages.showErrorDialog(myMainPanel, message, IdeBundle.message("dialog.title.convert.project"));
  }

  public boolean isConverted() {
    return myConverted;
  }
}
