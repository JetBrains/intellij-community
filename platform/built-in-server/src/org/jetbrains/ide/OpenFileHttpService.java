/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.ide;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.ManagingFS;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.RefreshSession;
import com.intellij.ui.AppUIUtil;
import com.intellij.util.Consumer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.builtInWebServer.WebServerPathToFileManager;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @api {get} /file Open file
 * @apiName file
 * @apiGroup Platform
 *
 * @apiParam {String} file The path of the file. Relative (to project base dir, VCS root, module source or content root) or absolute.
 * @apiParam {Integer} [line] The line number of the file (1-based).
 * @apiParam {Integer} [column] The column number of the file (1-based).
 * @apiParam {Boolean} [focused=true] Whether to focus project window.
 *
 * @apiExample {curl} Absolute path
 * curl http://localhost:63342/api/file//absolute/path/to/file.kt
 *
 * @apiExample {curl} Relative path
 * curl http://localhost:63342/api/file/relative/to/module/root/path/to/file.kt
 *
 * @apiExample {curl} With line and column
 * curl http://localhost:63342/api/file/relative/to/module/root/path/to/file.kt:100:34
 *
 * @apiExample {curl} Query parameters
 * curl http://localhost:63342/api/file?file=path/to/file.kt&line=100&column=34
 */
class OpenFileHttpService extends RestService {
  private static final RuntimeException NOT_FOUND = Promise.createError("not found");
  private static final Pattern LINE_AND_COLUMN = Pattern.compile("^(.*?)(?::(\\d+))?(?::(\\d+))?$");

  private volatile long refreshSessionId = 0;
  private final ConcurrentLinkedQueue<OpenFileTask> requests = new ConcurrentLinkedQueue<OpenFileTask>();

  @NotNull
  @Override
  protected String getServiceName() {
    return "file";
  }

  @Override
  protected boolean isMethodSupported(@NotNull HttpMethod method) {
    return method == HttpMethod.GET || method == HttpMethod.POST;
  }

  @Nullable
  @Override
  public String execute(@NotNull QueryStringDecoder urlDecoder, @NotNull FullHttpRequest request, @NotNull ChannelHandlerContext context) throws IOException {
    final boolean keepAlive = HttpUtil.isKeepAlive(request);
    final Channel channel = context.channel();

    OpenFileRequest apiRequest;
    if (request.method() == HttpMethod.POST) {
      apiRequest = gson.getValue().fromJson(createJsonReader(request), OpenFileRequest.class);
    }
    else {
      apiRequest = new OpenFileRequest();
      apiRequest.file = StringUtil.nullize(getStringParameter("file", urlDecoder), true);
      apiRequest.line = getIntParameter("line", urlDecoder);
      apiRequest.column = getIntParameter("column", urlDecoder);
      apiRequest.focused = getBooleanParameter("focused", urlDecoder, true);
    }

    int prefixLength = 1 + PREFIX.length() + 1 + getServiceName().length() + 1;
    String path = urlDecoder.path();
    if (path.length() > prefixLength) {
      Matcher matcher = LINE_AND_COLUMN.matcher(path).region(prefixLength, path.length());
      LOG.assertTrue(matcher.matches());
      if (apiRequest.file == null) {
        apiRequest.file = matcher.group(1).trim();
      }
      if (apiRequest.line == -1) {
        apiRequest.line = StringUtilRt.parseInt(matcher.group(2), 1);
      }
      if (apiRequest.column == -1) {
        apiRequest.column = StringUtilRt.parseInt(matcher.group(3), 1);
      }
    }

    if (apiRequest.file == null) {
      sendStatus(HttpResponseStatus.BAD_REQUEST, keepAlive, channel);
      return null;
    }

    openFile(apiRequest)
      .done(new Consumer<Void>() {
        @Override
        public void consume(Void aVoid) {
          sendStatus(HttpResponseStatus.OK, keepAlive, channel);
        }
      })
      .rejected(new Consumer<Throwable>() {
        @Override
        public void consume(Throwable throwable) {
          if (throwable == NOT_FOUND) {
            sendStatus(HttpResponseStatus.NOT_FOUND, keepAlive, channel);
          }
          else {
            // todo send error
            sendStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR, keepAlive, channel);
            LOG.error(throwable);
          }
        }
      });
    return null;
  }

  private static void navigate(@Nullable Project project, @NotNull VirtualFile file, @NotNull OpenFileRequest request) {
    if (project == null) {
      project = getLastFocusedOrOpenedProject();
      if (project == null) {
        project = ProjectManager.getInstance().getDefaultProject();
      }
    }

    // OpenFileDescriptor line and column number are 0-based.
    new OpenFileDescriptor(project, file, Math.max(request.line - 1, 0), Math.max(request.column - 1, 0)).navigate(true);
    if (request.focused) {
      com.intellij.ide.impl.ProjectUtil.focusProjectWindow(project, true);
    }
  }

  @NotNull
  Promise<Void> openFile(@NotNull OpenFileRequest request) {
    String path = FileUtil.expandUserHome(request.file);
    final File file = new File(FileUtil.toSystemDependentName(path));
    if (file.isAbsolute()) {
      return openAbsolutePath(file, request);
    }

    // we don't want to call refresh for each attempt on findFileByRelativePath call, so, we do what ourSaveAndSyncHandlerImpl does on frame activation
    RefreshQueue queue = RefreshQueue.getInstance();
    queue.cancelSession(refreshSessionId);
    OpenFileTask task = new OpenFileTask(FileUtil.toCanonicalPath(FileUtil.toSystemIndependentName(path), '/'), request);
    requests.offer(task);
    RefreshSession session = queue.createSession(true, true, new Runnable() {
      @Override
      public void run() {
        OpenFileTask task;
        while ((task = requests.poll()) != null) {
          try {
            if (openRelativePath(task.path, task.request)) {
              task.promise.setResult(null);
            }
            else {
              task.promise.setError(NOT_FOUND);
            }
          }
          catch (Throwable e) {
            task.promise.setError(e);
          }
        }
      }
    }, ModalityState.NON_MODAL);

    session.addAllFiles(ManagingFS.getInstance().getLocalRoots());
    refreshSessionId = session.getId();
    session.launch();
    return task.promise;
  }

  // path must be normalized
  private static boolean openRelativePath(@NotNull final String path, @NotNull final OpenFileRequest request) {
    VirtualFile virtualFile = null;
    Project project = null;

    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project openedProject : projects) {
      VirtualFile openedProjectBaseDir = openedProject.getBaseDir();
      if (openedProjectBaseDir != null) {
        virtualFile = openedProjectBaseDir.findFileByRelativePath(path);
      }

      if (virtualFile == null) {
        virtualFile = WebServerPathToFileManager.getInstance(openedProject).findVirtualFile(path);
      }
      if (virtualFile != null) {
        project = openedProject;
        break;
      }
    }

    if (virtualFile == null) {
      for (Project openedProject : projects) {
        for (VcsRoot vcsRoot : ProjectLevelVcsManager.getInstance(openedProject).getAllVcsRoots()) {
          VirtualFile root = vcsRoot.getPath();
          if (root != null) {
            virtualFile = root.findFileByRelativePath(path);
            if (virtualFile != null) {
              project = openedProject;
              break;
            }
          }
        }
      }
    }

    if (virtualFile == null) {
      return false;
    }

    final Project finalProject = project;
    final VirtualFile finalVirtualFile = virtualFile;
    AppUIUtil.invokeLaterIfProjectAlive(project, new Runnable() {
      @Override
      public void run() {
        navigate(finalProject, finalVirtualFile, request);
      }
    });
    return true;
  }

  @NotNull
  private static Promise<Void> openAbsolutePath(@NotNull final File file, @NotNull final OpenFileRequest request) {
    if (!file.exists()) {
      return Promise.reject(NOT_FOUND);
    }

    final AsyncPromise<Void> promise = new AsyncPromise<Void>();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        try {
          VirtualFile virtualFile;
          AccessToken token = WriteAction.start();
          try {
            virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
          }
          finally {
            token.finish();
          }

          if (virtualFile == null) {
            promise.setError(NOT_FOUND);
          }
          else {
            navigate(ProjectUtil.guessProjectForContentFile(virtualFile), virtualFile, request);
            promise.setResult(null);
          }
        }
        catch (Throwable e) {
          promise.setError(e);
        }
      }
    });
    return promise;
  }

  private static final class OpenFileTask {
    final String path;
    final OpenFileRequest request;

    final AsyncPromise<Void> promise = new AsyncPromise<Void>();

    OpenFileTask(@NotNull String path, @NotNull OpenFileRequest request) {
      this.path = path;
      this.request = request;
    }
  }

  static final class OpenFileRequest {
    public String file;
    // The line number of the file (1-based)
    public int line;
    // The column number of the file (1-based)
    public int column;

    public boolean focused = true;
  }
}
