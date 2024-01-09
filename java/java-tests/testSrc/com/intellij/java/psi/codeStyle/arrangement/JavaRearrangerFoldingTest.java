// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PRIVATE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC;

public class JavaRearrangerFoldingTest extends AbstractJavaRearrangerTest {
  public void test_that_doc_comment_folding_is_preserved() {
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 1;
    doTest("""
             import <fold>java.util.List;
             import java.util.Set;</fold>

             <fold text="/**...*/">/**
             * Class comment
             */</fold>
             class Test {

               <fold text="/**...*/>/**
                * Method comment
                */</fold>
               private void test(List<String> l) {}

               <fold text="/**...*/>/**
                * Another method comment
                */</fold>
               public void test(Set<String> s) {}
             }""", """
             import <fold>java.util.List;
             import java.util.Set;</fold>

             <fold text="/**...*/">/**
             * Class comment
             */</fold>
             class Test {

               <fold text="/**...*/>/**
                * Another method comment
                */</fold>
               public void test(Set<String> s) {}

               <fold text="/**...*/>/**
                * Method comment
                */</fold>
               private void test(List<String> l) {}
             }""", List.of(rule(PUBLIC), rule(PRIVATE)));
  }

  public void test_that_doc_comment_and_method_folding_is_preserved() {
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 1;
    doTest("""
             import java.util.List;
             import java.util.Set;

             class MyTest {
                 <fold text="/**...*/">/**
                  * comment 1
                  *
                  * @param s
                  */</fold>
                 private void test(String s) {
                 }

                 /**
                  * comment 2
                  *
                  * @param i
                  */
                 public void test(int i) {
                 }
             }""", """
             import java.util.List;
             import java.util.Set;

             class MyTest {
                 /**
                  * comment 2
                  *
                  * @param i
                  */
                 public void test(int i) {
                 }

                 <fold text="/**...*/">/**
                  * comment 1
                  *
                  * @param s
                  */</fold>
                 private void test(String s) {
                 }
             }""", List.of(rule(PUBLIC), rule(PRIVATE)));
  }

  public void test_that_single_doc_comment_folding_is_preserved() {
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 1;
    doTest("""
             package a.b;

             class MyTest {
                 /**
                  * private comment
                  *
                  * @param s
                  */
                 private void test(String s) {
                 }

                 /**
                  * comment 2
                  *
                  * @param i
                  */
                 public void test(int i) <fold text="{...}">{
                     System.out.println(1);
                 }</fold>
             }""", """
             package a.b;

             class MyTest {
                 /**
                  * comment 2
                  *
                  * @param i
                  */
                 public void test(int i) <fold text="{...}">{
                     System.out.println(1);
                 }</fold>

                 /**
                  * private comment
                  *
                  * @param s
                  */
                 private void test(String s) {
                 }
             }""", List.of(rule(PUBLIC), rule(PRIVATE)));
  }
}
