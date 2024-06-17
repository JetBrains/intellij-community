// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v201909

import com.jetbrains.jsonSchema.impl.light.StandardJsonSchemaVocabulary
import com.jetbrains.jsonSchema.impl.light.VocabularySchemaReferenceResolver

internal data object Vocabulary2019Resolver : VocabularySchemaReferenceResolver(
  listOf(
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/core",
                                         "https://json-schema.org/draft/2019-09/meta/core",
                                         "jsonSchema/vocabularies/2019-09/core.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/applicator",
                                         "https://json-schema.org/draft/2019-09/meta/applicator",
                                         "jsonSchema/vocabularies/2019-09/applicator.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/validation",
                                         "https://json-schema.org/draft/2019-09/meta/validation",
                                         "jsonSchema/vocabularies/2019-09/validation.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/meta-data",
                                         "https://json-schema.org/draft/2019-09/meta/meta-data",
                                         "jsonSchema/vocabularies/2019-09/meta-data.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/format",
                                         "https://json-schema.org/draft/2019-09/meta/format",
                                         "jsonSchema/vocabularies/2019-09/format.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2019-09/vocab/content",
                                         "https://json-schema.org/draft/2019-09/meta/content",
                                         "jsonSchema/vocabularies/2019-09/content.json")
  )
)