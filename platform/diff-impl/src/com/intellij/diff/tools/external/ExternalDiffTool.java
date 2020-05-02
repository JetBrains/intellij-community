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
package com.intellij.diff.tools.external;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManagerEx;
import com.intellij.diff.chains.*;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.execution.ExecutionException;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ListSelection;
import com.intellij.util.ThrowableConvertor;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExternalDiffTool {
  private static final Logger LOG = Logger.getInstance(ExternalDiffTool.class);

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
      final List<DiffRequest> requests = loadRequestsUnderProgress(project, chain);
      if (requests == null) return;

      List<DiffRequest> showInBuiltin = new ArrayList<>();
      for (DiffRequest request : requests) {
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
      LOG.warn(e);
      Messages.showErrorDialog(project, e.getMessage(), DiffBundle.message("can.t.show.diff.in.external.tool"));
    }
  }

  @Nullable
  private static List<DiffRequest> loadRequestsUnderProgress(@Nullable Project project,
                                                             @NotNull DiffRequestChain chain) throws Throwable {
    if (chain instanceof AsyncDiffRequestChain) {
      return computeWithModalProgress(project, DiffBundle.message("progress.title.loading.requests"), true, indicator -> {
        ListSelection<? extends DiffRequestProducer> listSelection = ((AsyncDiffRequestChain)chain).loadRequestsInBackground();
        return collectRequests(project, listSelection.getList(), listSelection.getSelectedIndex(), indicator);
      });
    }
    else {
      List<? extends DiffRequestProducer> allProducers = chain.getRequests();
      int index = chain.getIndex();

      return computeWithModalProgress(project, DiffBundle.message("progress.title.loading.requests"), true, indicator -> {
        return collectRequests(project, allProducers, index, indicator);
      });
    }
  }

  @NotNull
  private static List<DiffRequest> collectRequests(@Nullable Project project,
                                                   @NotNull List<? extends DiffRequestProducer> allProducers,
                                                   int index,
                                                   @NotNull ProgressIndicator indicator) {
    // TODO: show all changes on explicit selection (not only `chain.getIndex()` one)
    if (allProducers.isEmpty()) return Collections.emptyList();
    List<? extends DiffRequestProducer> producers = Collections.singletonList(allProducers.get(index));
    return collectRequests(project, producers, indicator);
  }

  @NotNull
  private static List<DiffRequest> collectRequests(@Nullable Project project,
                                                   @NotNull List<? extends DiffRequestProducer> producers,
                                                   @NotNull ProgressIndicator indicator) {
    List<DiffRequest> requests = new ArrayList<>();

    UserDataHolderBase context = new UserDataHolderBase();
    List<String> errorRequests = new ArrayList<>();

    for (DiffRequestProducer producer : producers) {
      try {
        requests.add(producer.process(context, indicator));
      }
      catch (DiffRequestProducerException e) {
        LOG.warn(e);
        errorRequests.add(producer.getName());
      }
    }

    if (!errorRequests.isEmpty()) {
      new Notification("Diff", DiffBundle.message("can.t.load.some.changes"), StringUtil.join(errorRequests, "<br>"), NotificationType.ERROR).notify(project);
    }

    return requests;
  }

  private static <T> T computeWithModalProgress(@Nullable Project project,
                                        @NotNull @Nls String title,
                                        boolean canBeCancelled,
                                        @NotNull ThrowableConvertor<? super ProgressIndicator, T, ? extends Exception> computable)
    throws Exception {
    return ProgressManager.getInstance().run(new Task.WithResult<T, Exception>(project, title, canBeCancelled) {
      @Override
      protected T compute(@NotNull ProgressIndicator indicator) throws Exception {
        return computable.convert(indicator);
      }
    });
  }

  public static void showRequest(@Nullable Project project, @NotNull DiffRequest request) throws ExecutionException, IOException {
    request.onAssigned(true);
    try {
      ExternalDiffSettings settings = ExternalDiffSettings.getInstance();

      List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
      List<String> titles = ((ContentDiffRequest)request).getContentTitles();

      ExternalDiffToolUtil.execute(project, settings, contents, titles, request.getTitle());
    }
    finally {
      request.onAssigned(false);
    }
  }

  public static boolean canShow(@NotNull DiffRequest request) {
    if (!(request instanceof ContentDiffRequest)) return false;
    List<DiffContent> contents = ((ContentDiffRequest)request).getContents();
    if (contents.size() != 2 && contents.size() != 3) return false;
    for (DiffContent content : contents) {
      if (!ExternalDiffToolUtil.canCreateFile(content)) return false;
    }
    return true;
  }
}
