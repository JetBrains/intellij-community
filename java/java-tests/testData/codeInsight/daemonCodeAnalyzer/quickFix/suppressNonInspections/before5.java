// "Suppress for statement" "true"
class a implements Runnable {
/**
 * @deprecated
 */
public void aa(){
}

public void run(){
    <caret>aa();
}
}
