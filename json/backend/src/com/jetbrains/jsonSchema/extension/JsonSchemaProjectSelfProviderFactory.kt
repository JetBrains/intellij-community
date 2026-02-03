// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension

import com.intellij.json.JsonBundle
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion
import org.jetbrains.annotations.Nls

class JsonSchemaProjectSelfProviderFactory : JsonSchemaProviderFactory, DumbAware {
  override fun getProviders(project: Project): List<JsonSchemaFileProvider> {
    return listOf(
      MyJsonSchemaFileProvider(project, BundledJsonSchemaInfo(JsonSchemaVersion.SCHEMA_4,
                                                              "schema.json",
                                                              "4",
                                                              "http://json-schema.org/draft-04/schema")),
      MyJsonSchemaFileProvider(project, BundledJsonSchemaInfo(JsonSchemaVersion.SCHEMA_6,
                                                              "schema06.json",
                                                              "6",
                                                              "http://json-schema.org/draft-06/schema")),
      MyJsonSchemaFileProvider(project, BundledJsonSchemaInfo(JsonSchemaVersion.SCHEMA_7,
                                                              "schema07.json",
                                                              "7",
                                                              "http://json-schema.org/draft-07/schema")),
      MyJsonSchemaFileProvider(project, BundledJsonSchemaInfo(JsonSchemaVersion.SCHEMA_2019_09,
                                                              "schema201909.json",
                                                              "2019-09",
                                                              "https://json-schema.org/draft/2019-09/schema")),
      MyJsonSchemaFileProvider(project, BundledJsonSchemaInfo(JsonSchemaVersion.SCHEMA_2020_12,
                                                              "schema202012.json",
                                                              "2020-12",
                                                              "https://json-schema.org/draft/2020-12/schema"))
    )
  }

  data class BundledJsonSchemaInfo(
    val version: JsonSchemaVersion,
    val bundledResourceFileName: String,
    val presentableSchemaId: @Nls String,
    val remoteSourceUrl: String
  )

  class MyJsonSchemaFileProvider(private val myProject: Project, private val myBundledSchema: BundledJsonSchemaInfo) : JsonSchemaFileProvider {

    override fun isAvailable(file: VirtualFile): Boolean {
      if (myProject.isDisposed) return false
      val service = JsonSchemaService.Impl.get(myProject)
      if (!service.isApplicableToFile(file)) return false
      val instanceSchemaVersion = service.getSchemaVersion(file)
      if (instanceSchemaVersion == null) return false
      return instanceSchemaVersion == myBundledSchema.version
    }

    override fun getSchemaVersion(): JsonSchemaVersion {
      return myBundledSchema.version
    }

    override fun getSchemaFile(): VirtualFile? {
      return JsonSchemaProviderFactory.getResourceFile(
        JsonSchemaProjectSelfProviderFactory::class.java,
        "/jsonSchema/${myBundledSchema.bundledResourceFileName}"
      )
    }

    override fun getSchemaType(): SchemaType {
      return SchemaType.schema
    }

    override fun getRemoteSource(): String {
      return myBundledSchema.remoteSourceUrl
    }

    override fun getPresentableName(): String {
      return JsonBundle.message("schema.of.version", myBundledSchema.presentableSchemaId)
    }

    override fun getName(): String {
      return presentableName
    }
  }
}
