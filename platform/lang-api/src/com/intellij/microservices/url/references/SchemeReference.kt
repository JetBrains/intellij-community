package com.intellij.microservices.url.references

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.PsiReferenceBase

class SchemeReference(
  val givenValue: String?,
  val supportedSchemes: List<String>,
  host: PsiLanguageInjectionHost,
  range: TextRange,
) : PsiReferenceBase.Immediate<PsiElement>(host, range, false, host), UrlSegmentReference