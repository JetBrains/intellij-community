package com.intellij.ide.util.treeView.smartTree;

import java.util.Collection;

public interface Grouper extends TreeAction{
  Collection<Group> group(Collection<TreeElement> children);
}
