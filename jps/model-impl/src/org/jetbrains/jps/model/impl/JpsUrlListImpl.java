package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.ex.JpsElementBase;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JpsUrlListImpl extends JpsElementBase<JpsUrlListImpl> implements JpsUrlList {
  private List<String> myUrls = new ArrayList<String>();

  public JpsUrlListImpl() {
  }

  public JpsUrlListImpl(JpsUrlListImpl list) {
    myUrls.addAll(list.myUrls);
  }

  @NotNull
  @Override
  public JpsUrlListImpl createCopy() {
    return new JpsUrlListImpl(this);
  }

  @NotNull
  @Override
  public List<String> getUrls() {
    return myUrls;
  }

  @Override
  public void addUrl(@NotNull String url) {
    myUrls.add(url);
    fireElementChanged();
  }

  @Override
  public void removeUrl(@NotNull String url) {
    myUrls.remove(url);
    fireElementChanged();
  }

  public void applyChanges(@NotNull JpsUrlListImpl modified) {
    if (!myUrls.equals(modified.myUrls)) {
      myUrls.clear();
      myUrls.addAll(modified.myUrls);
      fireElementChanged();
    }
  }
}
