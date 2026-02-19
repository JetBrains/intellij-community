// "Suppress for statement" "true"
class a implements Runnable {
/**
* @deprecated
*/
int b;
public void run(){
 <caret>b++;
}
}