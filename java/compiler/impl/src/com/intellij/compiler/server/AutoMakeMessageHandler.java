/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.compiler.server;

import com.intellij.compiler.CompilerMessageImpl;
import com.intellij.compiler.ProblemsView;
import com.intellij.notification.Notification;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.problems.Problem;
import com.intellij.problems.WolfTheProblemSolver;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CmdlineRemoteProto;
import org.jetbrains.jps.api.GlobalOptions;

import javax.swing.*;
import java.util.Collections;
import java.util.UUID;

/**
* @author Eugene Zhuravlev
*/
class AutoMakeMessageHandler extends DefaultMessageHandler {
  private static final Key<Notification> LAST_AUTO_MAKE_NOFITICATION = Key.create("LAST_AUTO_MAKE_NOFITICATION");
  private CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status myBuildStatus;
  private final Project myProject;
  private final WolfTheProblemSolver myWolf;
  private volatile boolean myUnprocessedFSChangesDetected = false;
  private final AutomakeCompileContext myContext;

  public AutoMakeMessageHandler(Project project) {
    super(project);
    myProject = project;
    myBuildStatus = CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.SUCCESS;
    myWolf = WolfTheProblemSolver.getInstance(project);
    myContext = new AutomakeCompileContext(project);
  }

  public boolean unprocessedFSChangesDetected() {
    return myUnprocessedFSChangesDetected;
  }

  @Override
  public void buildStarted(UUID sessionId) {
  }

  @Override
  protected void handleBuildEvent(UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.BuildEvent event) {
    if (myProject.isDisposed()) {
      return;
    }
    switch (event.getEventType()) {
      case BUILD_COMPLETED:
        myContext.getProgressIndicator().stop();
        if (event.hasCompletionStatus()) {
          final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status status = event.getCompletionStatus();
          myBuildStatus = status;
          if (status == CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.Status.CANCELED) {
            myContext.getProgressIndicator().cancel();
          }
        }
        final int errors = myContext.getMessageCount(CompilerMessageCategory.ERROR);
        final int warnings = myContext.getMessageCount(CompilerMessageCategory.WARNING);
        //noinspection SSBasedInspection
        SwingUtilities.invokeLater(() -> {
          if (myProject.isDisposed()) {
            return;
          }
          final CompilationStatusListener publisher = myProject.getMessageBus().syncPublisher(CompilerTopics.COMPILATION_STATUS);
          publisher.automakeCompilationFinished(errors, warnings, myContext);
        });
        return;

      case FILES_GENERATED:
        final CompilationStatusListener publisher = myProject.getMessageBus().syncPublisher(CompilerTopics.COMPILATION_STATUS);
        for (CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.GeneratedFile generatedFile : event.getGeneratedFilesList()) {
          final String root = FileUtil.toSystemIndependentName(generatedFile.getOutputRoot());
          final String relativePath = FileUtil.toSystemIndependentName(generatedFile.getRelativePath());
          publisher.fileGenerated(root, relativePath);
        }
        return;

      case CUSTOM_BUILDER_MESSAGE:
         if (event.hasCustomBuilderMessage()) {
           final CmdlineRemoteProto.Message.BuilderMessage.BuildEvent.CustomBuilderMessage message = event.getCustomBuilderMessage();
           if (GlobalOptions.JPS_SYSTEM_BUILDER_ID.equals(message.getBuilderId()) && GlobalOptions.JPS_UNPROCESSED_FS_CHANGES_MESSAGE_ID.equals(message.getMessageType())) {
             myUnprocessedFSChangesDetected = true;
           }
         }
         return;

      default:
    }
  }

  @Override
  protected void handleCompileMessage(final UUID sessionId, CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message) {
    if (myProject.isDisposed()) {
      return;
    }
    final CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind kind = message.getKind();
    if (kind == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.PROGRESS) {
      final ProblemsView view = ProblemsView.SERVICE.getInstance(myProject);
      if (message.hasDone()) {
        view.setProgress(message.getText(), message.getDone());
      }
      else {
        view.setProgress(message.getText());
      }
    }
    else {
      final CompilerMessageCategory category = convertToCategory(kind);
      if (category != null) { // only process supported kinds of messages
        final String sourceFilePath = message.hasSourceFilePath() ? message.getSourceFilePath() : null;
        final String url = sourceFilePath != null ? VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, FileUtil.toSystemIndependentName(sourceFilePath)) : null;
        final long line = message.hasLine() ? message.getLine() : -1;
        final long column = message.hasColumn() ? message.getColumn() : -1;
        final CompilerMessage msg = myContext.createAndAddMessage(category, message.getText(), url, (int)line, (int)column, null);
        if (kind == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.ERROR || kind == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.JPS_INFO) {
          if (kind == CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind.ERROR) {
            informWolf(myProject, message);
          }
          if (msg != null) {
            ProblemsView.SERVICE.getInstance(myProject).addMessage(msg, sessionId);
          }
        }
      }
    }
  }

  @Nullable
  private static CompilerMessageCategory convertToCategory(CmdlineRemoteProto.Message.BuilderMessage.CompileMessage.Kind kind) {
    switch(kind) {
      case ERROR: return CompilerMessageCategory.ERROR;
      case WARNING: return CompilerMessageCategory.WARNING;
      case INFO: return CompilerMessageCategory.INFORMATION;
      case JPS_INFO: return CompilerMessageCategory.INFORMATION;
      case OTHER: return CompilerMessageCategory.INFORMATION;
      default: return null;
    }
  }

  @Override
  public void handleFailure(UUID sessionId, CmdlineRemoteProto.Message.Failure failure) {
    if (myProject.isDisposed()) {
      return;
    }
    String descr = failure.hasDescription() ? failure.getDescription() : null;
    if (descr == null) {
      descr = failure.hasStacktrace()? failure.getStacktrace() : "";
    }
    final String msg = "Auto build failure: " + descr;
    CompilerManager.NOTIFICATION_GROUP.createNotification(msg, MessageType.INFO);
    ProblemsView.SERVICE.getInstance(myProject).addMessage(new CompilerMessageImpl(myProject, CompilerMessageCategory.ERROR, msg), sessionId);
  }

  @Override
  public void sessionTerminated(UUID sessionId) {
    String statusMessage = null/*"Auto make completed"*/;
    switch (myBuildStatus) {
      case SUCCESS:
        //statusMessage = "Auto make completed successfully";
        break;
      case UP_TO_DATE:
        //statusMessage = "All files are up-to-date";
        break;
      case ERRORS:
        statusMessage = "Auto build completed with errors";
        break;
      case CANCELED:
        //statusMessage = "Auto make has been canceled";
        break;
    }
    if (statusMessage != null) {
      final Notification notification = CompilerManager.NOTIFICATION_GROUP.createNotification(statusMessage, MessageType.INFO);
      if (!myProject.isDisposed()) {
        notification.notify(myProject);
      }
      myProject.putUserData(LAST_AUTO_MAKE_NOFITICATION, notification);
    } 
    else {
      Notification notification = myProject.getUserData(LAST_AUTO_MAKE_NOFITICATION);
      if (notification != null) {
        notification.expire();
        myProject.putUserData(LAST_AUTO_MAKE_NOFITICATION, null);
      }
    }
    if (!myProject.isDisposed()) {
      final ProblemsView view = ProblemsView.SERVICE.getInstance(myProject);
      view.clearProgress();
      view.clearOldMessages(null, sessionId);
    }
  }

  private void informWolf(Project project, CmdlineRemoteProto.Message.BuilderMessage.CompileMessage message) {
    final String srcPath = message.getSourceFilePath();
    if (srcPath != null && !project.isDisposed()) {
      final VirtualFile vFile = LocalFileSystem.getInstance().findFileByPath(srcPath);
      if (vFile != null) {
        final int line = (int)message.getLine();
        final int column = (int)message.getColumn();
        if (line > 0 && column > 0) {
          final Problem problem = myWolf.convertToProblem(vFile, line, column, new String[]{message.getText()});
          myWolf.weHaveGotProblems(vFile, Collections.singletonList(problem));
        }
        else {
          myWolf.queue(vFile);
        }
      }
    }
  }
}
