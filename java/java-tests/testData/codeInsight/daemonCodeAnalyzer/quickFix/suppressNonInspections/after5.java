// "Suppress for statement" "true"
class a implements Runnable {
/**
 * @deprecated
 */
public void aa(){
}

public void run(){
    //noinspection deprecation
    <caret>aa();
}
}
