package com.intellij.usages;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 21, 2004
 * Time: 9:15:39 PM
 * To change this template use File | Settings | File Templates.
 */
public class UsageViewPresentation {
  private String myTabText;
  private String myScopeText;
  private String myUsagesString;
  private String myTargetsNodeText = "Targets"; // Default value. to be overwritten in most cases.
  private String myNonCodeUsagesString = "Occurences found in non-java files";
  private String myCodeUsagesString = "Found usages";
  private boolean myShowReadOnlyStatusAsRed = false;
  private boolean myShowCancelButton = false;
  private boolean myOpenInNewTab = true;

  public UsageViewPresentation() {
  }

  public String getTabText() {
    return myTabText;
  }

  public void setTabText(String tabText) {
    myTabText = tabText;
  }

  public String getScopeText() {
    return myScopeText;
  }

  public void setScopeText(String scopeText) {
    myScopeText = scopeText;
  }

  public boolean isShowReadOnlyStatusAsRed() {
    return myShowReadOnlyStatusAsRed;
  }

  public void setShowReadOnlyStatusAsRed(boolean showReadOnlyStatusAsRed) {
    myShowReadOnlyStatusAsRed = showReadOnlyStatusAsRed;
  }

  public String getUsagesString() {
    return myUsagesString;
  }

  public void setUsagesString(String usagesString) {
    myUsagesString = usagesString;
  }

  public String getTargetsNodeText() {
    return myTargetsNodeText;
  }

  public void setTargetsNodeText(String targetsNodeText) {
    myTargetsNodeText = targetsNodeText;
  }

  public boolean isShowCancelButton() {
    return myShowCancelButton;
  }

  public void setShowCancelButton(boolean showCancelButton) {
    myShowCancelButton = showCancelButton;
  }

  public String getNonCodeUsagesString() {
    return myNonCodeUsagesString;
  }

  public void setNonCodeUsagesString(String nonCodeUsagesString) {
    myNonCodeUsagesString = nonCodeUsagesString;
  }

  public String getCodeUsagesString() {
    return myCodeUsagesString;
  }

  public void setCodeUsagesString(String codeUsagesString) {
    myCodeUsagesString = codeUsagesString;
  }

  public boolean isOpenInNewTab() {
    return myOpenInNewTab;
  }

  public void setOpenInNewTab(boolean openInNewTab) {
    myOpenInNewTab = openInNewTab;
  }
}

