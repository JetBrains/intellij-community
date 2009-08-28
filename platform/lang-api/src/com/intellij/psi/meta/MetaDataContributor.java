package com.intellij.psi.meta;

import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author yole
 */
public interface MetaDataContributor {
  ExtensionPointName<MetaDataContributor> EP_NAME = ExtensionPointName.create("com.intellij.metaDataContributor");

  void contributeMetaData(MetaDataRegistrar registrar);
}
