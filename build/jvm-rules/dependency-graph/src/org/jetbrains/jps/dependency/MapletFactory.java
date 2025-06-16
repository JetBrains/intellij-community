// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency;

public interface MapletFactory {
  <K, V> MultiMaplet<K, V> createSetMultiMaplet(
    String storageName, Externalizer<K> keyExternalizer, Externalizer<V> valueExternalizer
  );

  <K, V> Maplet<K, V> createMaplet(
    String storageName, Externalizer<K> keyExternalizer, Externalizer<V> valueExternalizer
  );
}
