package com.intellij.refactoring;

import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.util.xmlb.XmlSerializerUtil;

/**
 * @author yole
 */
@State(
  name = "BaseRefactoringSettings",
  storages = {
    @Storage(
      id ="other",
      file = "$APP_CONFIG$/other.xml"
    )}
)
public class RefactoringSettings implements PersistentStateComponent<RefactoringSettings> {
  public static RefactoringSettings getInstance() {
    return ServiceManager.getService(RefactoringSettings.class);
  }

  public boolean SAFE_DELETE_WHEN_DELETE = true;
  public boolean SAFE_DELETE_SEARCH_IN_COMMENTS = true;
  public boolean SAFE_DELETE_SEARCH_IN_NON_JAVA = true;

  public RefactoringSettings getState() {
    return this;
  }

  public void loadState(final RefactoringSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }
}
