package pkg;

/**
 * {@snippet file="Test.java"}
 * {@snippet class=Test}
 * {@snippet class=<error descr="Snippet file 'snippet-files/Test1.java' is not found">Test1</error>}
 * {@snippet class="Test"}
 * {@snippet class='Test''}
 * {@snippet class='sub.Test' region="reg"}
 * {@snippet class=<error descr="Snippet file 'snippet-files/sub/Test2.java' is not found">'sub.Test2'</error>}
 * {@snippet file="sub/test.txt"}
 * {@snippet file="sub/test.txt" region=<error descr="Region is not found">"notfound"</error>}
 */
public class SnippetRefs {
}