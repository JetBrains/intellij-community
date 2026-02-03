// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor;

import com.intellij.codeInsight.folding.JavaCodeFoldingSettings;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.options.BeanConfigurable;

final class JavaCodeFoldingOptionsProvider extends BeanConfigurable<JavaCodeFoldingSettings> implements CodeFoldingOptionsProvider {
  JavaCodeFoldingOptionsProvider() {
    super(JavaCodeFoldingSettings.getInstance(), JavaFileType.INSTANCE.getDescription());
    JavaCodeFoldingSettings settings = getInstance();

    checkBox(JavaBundle.message("checkbox.collapse.one.line.methods"), settings::isCollapseOneLineMethods, settings::setCollapseOneLineMethods);

    checkBox(JavaBundle.message("checkbox.collapse.simple.property.accessors"), settings::isCollapseAccessors, settings::setCollapseAccessors);

    checkBox(JavaBundle.message("checkbox.collapse.inner.classes"), settings::isCollapseInnerClasses, settings::setCollapseInnerClasses);

    checkBox(JavaBundle.message("checkbox.collapse.anonymous.classes"), settings::isCollapseAnonymousClasses, settings::setCollapseAnonymousClasses);

    checkBox(JavaBundle.message("checkbox.collapse.annotations"), settings::isCollapseAnnotations, settings::setCollapseAnnotations);

    checkBox(JavaBundle.message("checkbox.collapse.closures"), settings::isCollapseLambdas, settings::setCollapseLambdas);

    checkBox(JavaBundle.message("checkbox.collapse.generic.constructor.parameters"), settings::isCollapseConstructorGenericParameters, settings::setCollapseConstructorGenericParameters);

    checkBox(JavaBundle.message("checkbox.collapse.inferred.type"), settings::isReplaceVarWithInferredType, settings::setReplaceVarWithInferredType);

    checkBox(JavaBundle.message("checkbox.collapse.i18n.messages"), settings::isCollapseI18nMessages, settings::setCollapseI18nMessages);

    checkBox(JavaBundle.message("checkbox.collapse.suppress.warnings"), settings::isCollapseSuppressWarnings, settings::setCollapseSuppressWarnings);

    checkBox(JavaBundle.message("checkbox.collapse.end.of.line.comments"), settings::isCollapseEndOfLineComments, settings::setCollapseEndOfLineComments);

    checkBox(JavaBundle.message("checkbox.collapse.multiline.comments"), settings::isCollapseMultilineComments,
             settings::setCollapseMultilineComments);
  }
}