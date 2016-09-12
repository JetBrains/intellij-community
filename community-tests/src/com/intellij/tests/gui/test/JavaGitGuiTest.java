/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.tests.gui.test;

import com.intellij.dvcs.ui.CloneDvcsDialog;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.tests.gui.fixtures.*;
import com.intellij.tests.gui.framework.GuiTestCase;
import com.intellij.ui.EditorComboBox;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.junit.Ignore;
import org.junit.Test;

import static com.intellij.tests.gui.framework.GuiTests.findAndClickButton;

/**
 * Created by jetbrains on 06/09/16.
 */
public class JavaGitGuiTest extends GuiTestCase {

  @Ignore
  @Test
  public void testGitImport(){
    String gitPath = "https://github.com/karashevich/test.git";

    //wait welcome frame
    //get welcome frame
    WelcomeFrameFixture welcomeFrameFixture = WelcomeFrameFixture.find(myRobot);

    //click ActionLink "Check out from Version Control"
    welcomeFrameFixture.checkoutFrom();

    //click ListPopup menu item "Git"
    String vcsName = "Git";
    JBListPopupFixture.findListPopup(myRobot).invokeAction(vcsName);
    //find new dialog "Clone Repository"
    DialogFixture dialogFixture = new DialogFixture(myRobot, IdeaDialogFixture.find(myRobot, CloneDvcsDialog.class).dialog);
    //find EditorComboBox near label "Git Repository URL:"
    String message = DvcsBundle.message("clone.repository.url", vcsName);
    EditorComboBox editorComboBox = myRobot.finder().findByLabel(dialogFixture.target(), message, EditorComboBox.class);
    //enter gitPath
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        editorComboBox.setText(gitPath);
      }
    });

    //click button "Clone"
    findAndClickButton(dialogFixture, DvcsBundle.message("clone.button"));
    //find message with title "Checkout From Version Control"
    //click "Yes"
    MessagesFixture.findByTitle(myRobot, welcomeFrameFixture.target(), VcsBundle.message("checkout.title")).clickYes();
    //find new dialog "Import Project"
    DialogFixture importProjectFixture = DialogFixture.find(myRobot, "Import Project");
    //click "Next"
    findAndClickButton(importProjectFixture, "Next");
    //click "Next"
    findAndClickButton(importProjectFixture, "Next");
    //click "Next"
    findAndClickButton(importProjectFixture, "Next");
    //click "Next"
    findAndClickButton(importProjectFixture, "Next");
    //click "Next"
    findAndClickButton(importProjectFixture, "Next");
    //click "Next"
    findAndClickButton(importProjectFixture, "Next");
    //find message with title "No SDK Specified"
    //click "Ok"
    MessagesFixture.findByTitle(myRobot, welcomeFrameFixture.target(), IdeBundle.message("title.no.jdk.specified")).clickOk();
    //click "Finish"
    findAndClickButton(importProjectFixture, "Finish");

    //wait ideFrame
    IdeFrameFixture ideFrame = findIdeFrame();
    //wait background processes (indexing)
    ideFrame.waitForBackgroundTasksToFinish();

    //check java class "src/Test.java"
    //open Test.java in editor
    String testJavaPath = "src/Test.java";
    EditorFixture editor = ideFrame.getEditor();
    editor.open(testJavaPath);
  }
}
