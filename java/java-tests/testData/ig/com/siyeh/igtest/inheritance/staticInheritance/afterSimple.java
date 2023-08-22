// "Replace inheritance with qualified references in this class" "true"
public class ConstImpl <caret>{
    {
        int i = Const.DDDD;
    }
}
interface Const {
  int DDDD = 0;
}
