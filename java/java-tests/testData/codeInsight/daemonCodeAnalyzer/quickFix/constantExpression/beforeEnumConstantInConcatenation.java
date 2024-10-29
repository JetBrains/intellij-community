// "Fix all 'Constant expression can be evaluated' problems in file" "false"
import java.lang.annotation.RetentionPolicy;

class EnumConstantInContenation {
  
  void x() {
    System.out.println(RetentionPolicy.RUNTIME + "<caret> asdf")
  }
}