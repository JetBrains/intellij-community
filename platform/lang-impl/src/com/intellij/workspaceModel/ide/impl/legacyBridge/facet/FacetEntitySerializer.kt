// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.facet

import com.intellij.openapi.util.JDOMUtil
import com.intellij.workspaceModel.ide.impl.jps.serialization.CustomFacetEntitySerializer
import com.intellij.workspaceModel.storage.bridgeEntities.api.FacetEntity
import org.jdom.Element

class FacetEntitySerializer: CustomFacetEntitySerializer<FacetEntity> {
  override val entityType: Class<FacetEntity>
    get() = FacetEntity::class.java

  override fun serializeIntoXml(entity: FacetEntity): Element {
    return entity.configurationXmlTag?.let { JDOMUtil.load(it) }!!
  }
}