public class Bug7869 {
  public static void main(String[] args){
    String str = (<caret>str = String.valueOf(123.456d)) != null ? str : "default";
  }
}
