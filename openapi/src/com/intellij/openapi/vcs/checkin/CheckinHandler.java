package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class CheckinHandler {

  public enum ReturnResult {
    COMMIT, CANCEL, CLOSE_WINDOW
  }

  @Nullable
  public RefreshableOnComponent getBeforeCheckinConfigurationPanel(){
    return null;
  }

  @Nullable
  public RefreshableOnComponent getAfterCheckinConfigurationPanel(){
    return null;
  }

  public ReturnResult beforeCheckin(CheckinProjectPanel checkinPanel){
    return ReturnResult.COMMIT;
  }

  public void checkinSuccessful(){

  }

  public void checkinFailed(List<VcsException> exception){

  }

}
