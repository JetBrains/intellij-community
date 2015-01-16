package com.intellij.openapi.util.diff.impl;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.diff.actions.impl.GoToChangePopupBuilder;
import com.intellij.openapi.util.diff.chains.DiffRequestChain;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentable;
import com.intellij.openapi.util.diff.chains.DiffRequestPresentableException;
import com.intellij.openapi.util.diff.requests.*;
import com.intellij.openapi.util.diff.tools.util.SoftHardCacheMap;
import com.intellij.openapi.util.diff.util.DiffUserDataKeys.ScrollToPolicy;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class CacheDiffRequestChainProcessor extends DiffRequestProcessor {
  private static final Logger LOG = Logger.getInstance(CacheDiffRequestChainProcessor.class);

  @NotNull private final DiffRequestChain myRequestChain;

  @NotNull private final SoftHardCacheMap<DiffRequestPresentable, DiffRequest> myRequestCache =
    new SoftHardCacheMap<DiffRequestPresentable, DiffRequest>(5, 5);

  public CacheDiffRequestChainProcessor(@Nullable Project project, @NotNull DiffRequestChain requestChain) {
    super(project);
    myRequestChain = requestChain;
  }

  @Override
  public void init() {
    super.init();

    if (myRequestChain.getRequests().isEmpty()) {
      applyRequest(new NoDiffRequest(), true, null);
    }
    else {
      applyRequest(new LoadingDiffRequest(), true, null);
    }
  }

  //
  // Update
  //

  public void updateRequest(boolean force, @Nullable ScrollToPolicy scrollToChangePolicy) {
    applyRequest(loadRequest(), force, scrollToChangePolicy);
  }

  @NotNull
  private DiffRequest loadRequest() {
    List<? extends DiffRequestPresentable> requests = myRequestChain.getRequests();
    int index = myRequestChain.getIndex();

    if (index < 0 || index >= requests.size()) return new NoDiffRequest();

    final DiffRequestPresentable presentable = requests.get(index);

    DiffRequest request = myRequestCache.get(presentable);
    if (request != null) return request;

    final Error[] errorRef = new Error[1];
    final DiffRequest[] requestRef = new DiffRequest[1];
    ProgressManager.getInstance().run(new Task.Modal(getProject(), "Collecting data", true) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          requestRef[0] = presentable.process(getContext(), indicator);
        }
        catch (ProcessCanceledException e) {
          requestRef[0] = new OperationCanceledDiffRequest(presentable.getName()); // TODO: add reload action
        }
        catch (DiffRequestPresentableException e) {
          requestRef[0] = new ErrorDiffRequest(presentable, e);
        }
        catch (Exception e) {
          requestRef[0] = new ErrorDiffRequest(presentable, e);
        }
        catch (Error e) {
          errorRef[0] = e;
        }
      }
    });
    if (errorRef[0] != null) throw errorRef[0];

    request = requestRef[0];
    assert request != null;

    myRequestCache.put(presentable, request);

    return request;
  }

  //
  // Abstract
  //

  protected void setWindowTitle(@NotNull String title) {
  }

  protected void onAfterNavigate() {
  }

  //
  // Misc
  //

  @Override
  protected void onDispose() {
    super.onDispose();
    myRequestCache.clear();
  }

  @Nullable
  @Override
  public <T> T getContextUserData(@NotNull Key<T> key) {
    return myRequestChain.getUserData(key);
  }

  @Override
  public <T> void putContextUserData(@NotNull Key<T> key, @Nullable T value) {
    myRequestChain.putUserData(key, value);
  }

  @NotNull
  @Override
  protected List<AnAction> getNavigationActions() {
    return ContainerUtil.list(
      new MyPrevDifferenceAction(),
      new MyNextDifferenceAction(),
      new MyPrevChangeAction(),
      new MyNextChangeAction(),
      createGoToChangeAction()
    );
  }

  //
  // Getters
  //

  @NotNull
  public DiffRequestChain getRequestChain() {
    return myRequestChain;
  }

  //
  // Navigation
  //

  @Override
  protected boolean hasNextChange() {
    return myRequestChain.getIndex() < myRequestChain.getRequests().size() - 1;
  }

  @Override
  protected boolean hasPrevChange() {
    return myRequestChain.getIndex() > 0;
  }

  @Override
  protected void goToNextChange(boolean fromDifferences) {
    myRequestChain.setIndex(myRequestChain.getIndex() + 1);
    updateRequest(false, fromDifferences ? ScrollToPolicy.FIRST_CHANGE : null);
  }

  @Override
  protected void goToPrevChange(boolean fromDifferences) {
    myRequestChain.setIndex(myRequestChain.getIndex() - 1);
    updateRequest(false, fromDifferences ? ScrollToPolicy.LAST_CHANGE : null);
  }

  @Override
  protected boolean isNavigationEnabled() {
    return myRequestChain.getRequests().size() > 1;
  }

  @NotNull
  private AnAction createGoToChangeAction() {
    return GoToChangePopupBuilder.create(myRequestChain, new Consumer<Integer>() {
      @Override
      public void consume(Integer index) {
        if (index >= 0 && index != myRequestChain.getIndex()) {
          myRequestChain.setIndex(index);
          updateRequest();
        }
      }
    });
  }
}
