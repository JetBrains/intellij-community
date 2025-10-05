// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.javaDoc

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.java.JavaBundle
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.Contract
import java.util.*
import java.util.function.Predicate
import java.util.stream.Collectors

public class ReplaceWithJavadocInspection : LocalInspectionTool() {

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : JavaElementVisitor() {
      override fun visitComment(element: PsiComment) {
        super.visitComment(element)

        if (element is PsiDocComment) return

        val parent: PsiElement = element.getParent()

        if (parent !is PsiMember || parent is PsiAnonymousClass) return
        val type: PsiElement? = PsiTreeUtil.getPrevSiblingOfType(element, PsiModifierList::class.java)
        if (type != null) return

        val fix = ReplaceWithJavadocFix()
        holder.registerProblem(element, JavaBundle.message("inspection.replace.with.javadoc.comment"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fix)
      }
    }
  }

  public class ReplaceWithJavadocFix : PsiUpdateModCommandQuickFix() {
    override fun getFamilyName(): String {
      return JavaBundle.message("inspection.replace.with.javadoc")
    }

    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      if (element !is PsiComment) return
      val method = element.parent ?: return

      val child = method.firstChild as? PsiComment ?: return

      val psiFacade = JavaPsiFacade.getInstance(element.project)
      val factory = psiFacade.elementFactory

      // the set will contain all the comment nodes that are directly before the method's modifier list
      val commentNodes = mutableSetOf<PsiComment>()

      val javadocText = prepareJavadocComment(child, commentNodes)
      val javadoc = factory.createDocCommentFromText(javadocText)

      if (commentNodes.isEmpty()) {
        thisLogger().error("The set of visited node comments is empty")
        return
      }

      if (commentNodes.size > 1) {
        // remove all the comment nodes except the first one
        commentNodes.stream().skip(1).forEach { obj: PsiComment -> obj.delete() }
      }

      val item = ContainerUtil.getFirstItem(commentNodes)
      item.replace(javadoc)
    }

    @Contract(mutates = "param2")
    private fun prepareJavadocComment(comment: PsiComment, visited: MutableSet<PsiComment>): String {
      val commentContent: Collection<String> = siblingsComments(comment, visited)
      val sb = StringBuilder("/**\n")
      for (string in commentContent) {
        val line = string.trim { it <= ' ' }
        if (line.isEmpty()) continue
        sb.append("* ")
        sb.append(line)
        sb.append("\n")
      }
      if (comment is PsiDocComment) {
        val tags = comment.tags
        if (tags.size > 0) {
          val start = tags[0].startOffsetInParent
          val end = tags[tags.size - 1].textRangeInParent.endOffset
          sb.append("* ")
          sb.append(comment.getText(), start, end)
          sb.append("\n")
        }
      }
      sb.append("*/")
      return sb.toString()
    }

    /**
     * Extracts the content of the comment nodes that are right siblings to the comment node
     *
     * @param comment a comment node to get siblings for
     * @param visited a set of visited comment nodes
     * @return the list of strings which consists of the content of the comment nodes that are either left or right siblings.
     */
    @Contract(mutates = "param2")
    private fun siblingsComments(comment: PsiComment,
                                 visited: MutableSet<in PsiComment>): List<String> {
      val result = ArrayList<String>()
      var node: PsiElement? = comment
      do {
        if (node is PsiComment) {
          val commentNode = node
          visited.add(commentNode)
          result.addAll(getCommentTextLines(commentNode))
        }
        node = node!!.nextSibling
      }
      while (node != null && node !is PsiModifierList)
      return result
    }

    /**
     * Extracts the content of a comment as a list of strings.
     *
     * @param comment [PsiComment] to examine
     * @return the content of a comment as a list of strings where each line is an element of the list.
     */
    @Contract(pure = true)
    private fun getCommentTextLines(comment: PsiComment): Collection<String> {
      val lines = if (comment is PsiDocComment) {
        Arrays.stream(comment.descriptionElements)
          .map { obj: PsiElement -> obj.text }
      }
      else {
        Arrays.stream(extractLines(comment))
      }
      return lines
        .map { obj: String -> obj.trim { it <= ' ' } }
        .filter(Predicate.not { obj: String -> obj.isEmpty() })
        .map { line: String ->
          if (line.startsWith("*")) line.substring(1)
          else line
        }
        .map { line: String? ->
          StringUtil.replace(
            line!!, "*/", "*&#47;")
        }
        .collect(Collectors.toList())
    }

    /**
     * Extracts the content from either a C-style block comment or an end-of-line comment as an array of lines
     *
     * @param comment [PsiComment] to examine
     * @return the content of a comment as an array of lines
     */
    @Contract(pure = true)
    private fun extractLines(comment: PsiComment): Array<String> {
      assert(comment !is PsiDocComment) { "The method can't be called for a javadoc comment." }
      val commentText = comment.text
      if (comment.tokenType === JavaTokenType.END_OF_LINE_COMMENT) {
        return arrayOf(commentText.substring("//".length))
      }
      val start = "/*".length
      val end = commentText.length - "*/".length
      val content = commentText.substring(start, end)
      return content.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    }
  }
}
