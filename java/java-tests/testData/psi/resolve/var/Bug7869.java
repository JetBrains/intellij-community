/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 22.01.2003
 * Time: 14:50:35
 * To change this template use Options | File Templates.
 */
public class Bug7869 {
  public static void main(String[] args){
    String str = (<ref>str = String.valueOf(123.456d)) != null ? str : "default";
  }
}
