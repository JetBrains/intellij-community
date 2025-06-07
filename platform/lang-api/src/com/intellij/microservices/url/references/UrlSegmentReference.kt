package com.intellij.microservices.url.references

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference

interface UrlSegmentReference : PsiReference

interface UrlSegmentReferenceTarget: PsiElement