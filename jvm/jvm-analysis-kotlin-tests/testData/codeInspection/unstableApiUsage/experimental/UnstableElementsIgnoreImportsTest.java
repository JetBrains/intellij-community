// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import pkg.AnnotatedClass;
import static pkg.AnnotatedClass.NON_ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS;
import static pkg.AnnotatedClass.staticNonAnnotatedMethodInAnnotatedClass;
import static pkg.AnnotatedClass.ANNOTATED_CONSTANT_IN_ANNOTATED_CLASS;
import static pkg.AnnotatedClass.staticAnnotatedMethodInAnnotatedClass;

import pkg.NonAnnotatedClass;
import static pkg.NonAnnotatedClass.NON_ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
import static pkg.NonAnnotatedClass.staticNonAnnotatedMethodInNonAnnotatedClass;
import static pkg.NonAnnotatedClass.ANNOTATED_CONSTANT_IN_NON_ANNOTATED_CLASS;
import static pkg.NonAnnotatedClass.staticAnnotatedMethodInNonAnnotatedClass;

import pkg.AnnotatedEnum;
import pkg.NonAnnotatedEnum;
import static pkg.AnnotatedEnum.NON_ANNOTATED_VALUE_IN_ANNOTATED_ENUM;
import static pkg.AnnotatedEnum.ANNOTATED_VALUE_IN_ANNOTATED_ENUM;
import static pkg.NonAnnotatedEnum.NON_ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;
import static pkg.NonAnnotatedEnum.ANNOTATED_VALUE_IN_NON_ANNOTATED_ENUM;

import pkg.AnnotatedAnnotation;
import pkg.NonAnnotatedAnnotation;

import annotatedPkg.ClassInAnnotatedPkg;