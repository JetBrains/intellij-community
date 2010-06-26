// "Suppress for statement" "true"
class a {
/**
 * @deprecated
 */
public void aa(){
}

private void aaa(){
    //noinspection deprecation
    <caret>aa();
}
}
