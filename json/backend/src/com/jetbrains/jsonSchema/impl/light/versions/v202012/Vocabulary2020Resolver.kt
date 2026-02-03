// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light.versions.v202012

import com.jetbrains.jsonSchema.impl.light.StandardJsonSchemaVocabulary
import com.jetbrains.jsonSchema.impl.light.VocabularySchemaReferenceResolver

internal data object Vocabulary2020Resolver : VocabularySchemaReferenceResolver(
  listOf(
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/core",
                                         "https://json-schema.org/draft/2020-12/meta/core",
                                         "jsonSchema/vocabularies/2020-12/core.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/applicator",
                                         "https://json-schema.org/draft/2020-12/meta/applicator",
                                         "jsonSchema/vocabularies/2020-12/applicator.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/unevaluated",
                                         "https://json-schema.org/draft/2020-12/meta/unevaluated",
                                         "jsonSchema/vocabularies/2020-12/unevaluated.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/validation",
                                         "https://json-schema.org/draft/2020-12/meta/validation",
                                         "jsonSchema/vocabularies/2020-12/validation.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/meta-data",
                                         "https://json-schema.org/draft/2020-12/meta/meta-data",
                                         "jsonSchema/vocabularies/2020-12/meta-data.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/format-annotation",
                                         "https://json-schema.org/draft/2020-12/meta/format-annotation",
                                         "jsonSchema/vocabularies/2020-12/format-annotation.json"),
    StandardJsonSchemaVocabulary.Bundled("https://json-schema.org/draft/2020-12/vocab/content",
                                         "https://json-schema.org/draft/2020-12/meta/content",
                                         "jsonSchema/vocabularies/2020-12/content.json")
  )
)