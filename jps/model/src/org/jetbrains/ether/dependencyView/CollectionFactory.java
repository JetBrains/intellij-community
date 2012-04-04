package org.jetbrains.ether.dependencyView;

import java.util.Collection;

/**
* @author Eugene Zhuravlev
*         Date: 4/3/12
*/
public interface CollectionFactory<X> {
  Collection<X> create();
}
