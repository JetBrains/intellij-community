package com.intellij.openapi.util.diff.tools.external;

import com.intellij.execution.ExecutionException;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.diff.DiffDialogHints;
import com.intellij.openapi.util.diff.DiffManagerEx;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.chains.SimpleDiffRequestChain;
import com.intellij.openapi.util.diff.contents.DiffContent;
import com.intellij.openapi.util.diff.requests.ContentDiffRequest;
import com.intellij.openapi.util.diff.requests.DiffRequest;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ExternalDiffTool {
  public static final Logger LOG = Logger.getInstance(ExternalDiffTool.class);

  public static boolean isDefault() {
    return ExternalDiffSettings.getInstance().isDiffEnabled() && ExternalDiffSettings.getInstance().isDiffDefault();
  }

  public static boolean isEnabled() {
    return ExternalDiffSettings.getInstance().isDiffEnabled();
  }

  public static void show(@Nullable final Project project,
                          @NotNull final DiffRequestChain chain,
                          @NotNull final DiffDialogHints hints) {
    try {
      //noinspection unchecked
      final Ref<List<DiffRequest>> requestsRef = new Ref<List<DiffRequest>>();
      final Ref<Throwable> exceptionRef = new Ref<Throwable>();
      ProgressManager.getInstance().run(new Task.Modal(project, "Loading Requests", true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            requestsRef.set(collectRequests(project, chain, indicator));
          }
          catch (Throwable e) {
            exceptionRef.set(e);
          }
        }
      });

      if (!exceptionRef.isNull()) throw exceptionRef.get();

      List<DiffRequest> showInBuiltin = new ArrayList<DiffRequest>();
      for (DiffRequest request : requestsRef.get()) {
        if (canShow(request)) {
          showRequest(project, request);
        }
        else {
          showInBuiltin.add(request);
        }
      }

      if (!showInBuiltin.isEmpty()) {
        DiffManagerEx.getInstance().showDiffBuiltin(project, new SimpleDiffRequestChain(showInBuiltin), hints);
      }
    }
    catch (ProcessCanceledException ignore) {
    }
    catch (Throwable e) {
      LOG.error(e);
      Messages.showErrorDialog(project, e.getMessage(), "Can't Show Diff In External Tool");
    }
  }

  @NotNull
  private static List<DiffRequest> collectRequests(@Nullable Project project,
                                                   @NotNull final DiffRequestChain chain,
                                                   @NotNull ProgressIndicator indicator)
    throws IOException, ExecutionException {
    List<DiffRequest> requests = new ArrayList<DiffRequest>();

    UserDataHolderBase context = new UserDataHolderBase();
    List<String> errorRequests = new ArrayList<String>();

    for (DiffRequestPresentable presentable : chain.getRequests()) {
      try {
        requests.add(presentable.process(context, indicator));
      }
      catch (DiffRequestPresentableException e) {
        LOG.warn(e);
        errorRequests.add(presentable.getName());
      }
    }

    if (!errorRequests.isEmpty()) {
      new Notification("diff", "Can't load some changes", StringUtil.join(errorRequests, "<br>"), NotificationType.ERROR).notify(project);
    }

    return requests;
  }

  public static void showRequest(@Nullable Project project, @NotNull DiffRequest request)
    throws ExecutionException, IOException {
    request.onAssigned(true);

    ExternalDiffSettings settings = ExternalDiffSettings.getInstance();

    DiffContent[] contents = ((ContentDiffRequest)request).getContents();
    String[] titles = ((ContentDiffRequest)request).getContentTitles();

    ExternalDiffToolUtil.execute(settings, contents, titles, request.getWindowTitle());

    request.onAssigned(false);
  }

  public static boolean canShow(@NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;
    DiffContent[] contents = ((ContentDiffRequest)request).getContents();
    if (contents.length != 2 && contents.length != 3) return false;
    for (DiffContent content : contents) {
      if (!ExternalDiffToolUtil.canCreateFile(content)) return false;
    }
    return true;
  }
}
