package org.jetbrains.jps.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.*;

import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class JpsUrlListImpl extends JpsElementBase<JpsUrlListImpl> implements JpsUrlList {
  private List<String> myUrls = new ArrayList<String>();

  public JpsUrlListImpl(JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    super(eventDispatcher, parent);
  }

  public JpsUrlListImpl(JpsUrlListImpl list, JpsEventDispatcher dispatcher, JpsParentElement parent) {
    super(list, dispatcher, parent);
    myUrls.addAll(list.myUrls);
  }

  @NotNull
  @Override
  public JpsUrlListImpl createCopy(@NotNull JpsModel model, @NotNull JpsEventDispatcher eventDispatcher, JpsParentElement parent) {
    return new JpsUrlListImpl(this, eventDispatcher, parent);
  }

  @NotNull
  @Override
  public List<String> getUrls() {
    return myUrls;
  }

  @Override
  public void addUrl(@NotNull String url) {
    myUrls.add(url);
    getEventDispatcher().fireElementChanged(this);
  }

  @Override
  public void removeUrl(@NotNull String url) {
    myUrls.remove(url);
    getEventDispatcher().fireElementChanged(this);
  }

  public void applyChanges(@NotNull JpsUrlListImpl modified) {
    if (!myUrls.equals(modified.myUrls)) {
      myUrls.clear();
      myUrls.addAll(modified.myUrls);
      getEventDispatcher().fireElementChanged(this);
    }
  }
}
