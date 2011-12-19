package com.intellij.unscramble;

import junit.framework.TestCase;
import org.jetbrains.annotations.NonNls;

/**
 * @author cdr
 */
public class NormalizeTextTest extends TestCase {
  public void testCausedBy() {
    doTest("javax.faces.FacesException: Error calling action method of component with id _id6:_id10\n" +
           "        at java.lang.Th\n" +
           "read.run(Thread.java:626)\n" +
           "Caused by: javax.faces.el.EvaluationException: Exception while invoking expression #{_loginAction.login}\n" +
           "        at org.apache.myfaces.el.MethodBindingImpl.invoke(MethodBindingImpl.java:153)\n" +
           "        at org.apache.myfaces.application.ActionListenerImpl.processAction(ActionListenerImpl.java:63)\n",

           "javax.faces.FacesException: Error calling action method of component with id _id6:_id10\n" +
           "        at java.lang.Thread.run(Thread.java:626)\n" +
           "Caused by: javax.faces.el.EvaluationException: Exception while invoking expression #{_loginAction.login}\n" +
           "        at org.apache.myfaces.el.MethodBindingImpl.invoke(MethodBindingImpl.java:153)\n" +
           "        at org.apache.myfaces.application.ActionListenerImpl.processAction(ActionListenerImpl.java:63)"
           );
  }

  public void testThreadNames() {
    doTest("\"Background process\" prio=6 tid=0x21193b88 nid=0x11ea4 waiting on condition [0x2\n" + "2cbf000..0x22cbfd68]\n" +
           "        at java.lang.Thread.sleep(Native Method)\n" + "        at com.intellij.util.ui.Timer$1.run(Timer.java:23)\n" + "\n" +
           "\"Alarm pool\" prio=6 tid=0x37cacbe0 nid=0x7940 waiting on condition [0x3972f000..\n" + "0x3972fae8]\n" +
           "        at sun.misc.Unsafe.park(Native Method)\n" +
           "        at java.util.concurrent.locks.LockSupport.park(LockSupport.java:118)\n" +
           "        at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject\n" +
           ".await(AbstractQueuedSynchronizer.java:1767)\n\n" +
           "        at java.util.concurrent.LinkedBlockingQueue.take(LinkedBlockingQueue.jav\n" + "a:359)\n" +
           "        at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.ja\n" + "va:470)\n" +
           "        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor\n" + ".java:674)\n" +
           "        at java.lang.Thread.run(Thread.java:595)",


           "\"Background process\" prio=6 tid=0x21193b88 nid=0x11ea4 waiting on condition [0x22cbf000..0x22cbfd68]\n" +
           "        at java.lang.Thread.sleep(Native Method)\n" +
           "        at com.intellij.util.ui.Timer$1.run(Timer.java:23)\n" +
           "\n" +
           "\"Alarm pool\" prio=6 tid=0x37cacbe0 nid=0x7940 waiting on condition [0x3972f000..0x3972fae8]\n" +
           "        at sun.misc.Unsafe.park(Native Method)\n" +
           "        at java.util.concurrent.locks.LockSupport.park(LockSupport.java:118)\n" +
           "        at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.await(AbstractQueuedSynchronizer.java:1767)\n" +
           "        at java.util.concurrent.LinkedBlockingQueue.take(LinkedBlockingQueue.java:359)\n" +
           "        at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:470)\n" +
           "        at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:674)\n" +
           "        at java.lang.Thread.run(Thread.java:595)"
           );
  }

  public void testLocked() {
    doTest("       at com.intellij.lang.jsp.JspFileViewProviderImpl.getKeyPrefixes(JspFileV\n" + "iewProviderImpl.java:76)\n" +
           "        at com.intellij.lang.jsp.JspFileViewProviderImpl.getKnownTaglibPrefixes(\n" + "JspFileViewProviderImpl.java:89)\n" +
           "        - locked <0x04cc0768> (a java.lang.Object)\n" +
           "        at com.intellij.psi.impl.source.parsing.jsp.JspLexer.doInitOriginal(JspL\n" + "exer.java:39)\n" +
           "        at com.intellij.psi.impl.source.parsing.jsp.JspLexer.start(JspLexer.java\n" + ":49)",

           "       at com.intellij.lang.jsp.JspFileViewProviderImpl.getKeyPrefixes(JspFileViewProviderImpl.java:76)\n" +
           "        at com.intellij.lang.jsp.JspFileViewProviderImpl.getKnownTaglibPrefixes(JspFileViewProviderImpl.java:89)\n" +
           "        - locked <0x04cc0768> (a java.lang.Object)\n" +
           "        at com.intellij.psi.impl.source.parsing.jsp.JspLexer.doInitOriginal(JspLexer.java:39)\n" +
           "        at com.intellij.psi.impl.source.parsing.jsp.JspLexer.start(JspLexer.java:49)");
  }
  public void testAtSplit() {
    doTest("java.lang.Throwable\n" +
           "at\n" +
           "com.intellij.openapi.util.objectTree.ObjectNode.<init>(ObjectNode.jav\n" + "a:31)\n" +
           "at\n" +
           "com.intellij.openapi.util.objectTree.ObjectTree.getNodeFor(ObjectTree\n" + ".java:79)",

           "java.lang.Throwable\n" +
           "at com.intellij.openapi.util.objectTree.ObjectNode.<init>(ObjectNode.java:31)\n" +
           "at com.intellij.openapi.util.objectTree.ObjectTree.getNodeFor(ObjectTree.java:79)");
  }
  
  public void testSplitMergedLines() {
    doTest("org.apache.velocity.exception.MethodInvocationException " +
           "at org.apache.velocity.runtime.parser.node.ASTMethod.execute(ASTMethod.java:246) " +
           "at org.apache.velocity.runtime.parser.node.ASTReference.execute(ASTReference.java:175) " +
           "at org.apache.velocity.runtime.parser.node.ASTReference.value(ASTReference.java:327) " +
           "at org.apache.velocity.runtime.parser.node.ASTExpression.value(ASTExpression.java:51) " +
           "at org.apache.velocity.runtime.parser.node.ASTSetDirective.render(ASTSetDirective.java:95) " +
           "at org.apache.velocity.runtime.parser.node.ASTBlock.render(ASTBlock.java:55) " +
           "at org.apache.velocity.runtime.directive.Foreach.render(Foreach.java:166) " +
           "at org.apache.velocity.runtime.parser.node.ASTDirective.render(ASTDirective.java:114) " +
           "at org.apache.velocity.runtime.parser.node.SimpleNode.render(SimpleNode.java:230) " +
           "at org.apache.velocity.runtime.directive.VelocimacroProxy.render(VelocimacroProxy.java:172) " +
           "at org.apache.velocity.runtime.parser.node.ASTDirective.render(ASTDirective.java:114) " +
           "at org.apache.velocity.runtime.parser.node.SimpleNode.render(SimpleNode.java:230) " +
           "at org.apache.velocity.Template.merge(Template.java:256)",
           
           "org.apache.velocity.exception.MethodInvocationException\n" +
           "at org.apache.velocity.runtime.parser.node.ASTMethod.execute(ASTMethod.java:246)\n" +
           "at org.apache.velocity.runtime.parser.node.ASTReference.execute(ASTReference.java:175)\n" +
           "at org.apache.velocity.runtime.parser.node.ASTReference.value(ASTReference.java:327)\n" +
           "at org.apache.velocity.runtime.parser.node.ASTExpression.value(ASTExpression.java:51)\n" +
           "at org.apache.velocity.runtime.parser.node.ASTSetDirective.render(ASTSetDirective.java:95)\n" +
           "at org.apache.velocity.runtime.parser.node.ASTBlock.render(ASTBlock.java:55)\n" +
           "at org.apache.velocity.runtime.directive.Foreach.render(Foreach.java:166)\n" +
           "at org.apache.velocity.runtime.parser.node.ASTDirective.render(ASTDirective.java:114)\n" +
           "at org.apache.velocity.runtime.parser.node.SimpleNode.render(SimpleNode.java:230)\n" +
           "at org.apache.velocity.runtime.directive.VelocimacroProxy.render(VelocimacroProxy.java:172)\n" +
           "at org.apache.velocity.runtime.parser.node.ASTDirective.render(ASTDirective.java:114)\n" +
           "at org.apache.velocity.runtime.parser.node.SimpleNode.render(SimpleNode.java:230)\n" +
           "at org.apache.velocity.Template.merge(Template.java:256)");
  }

  private static void doTest(@NonNls String stackTrace, @NonNls String expected) {
    String normalized = UnscrambleDialog.normalizeText(stackTrace);
    assertEquals(expected, normalized);
  }
}
