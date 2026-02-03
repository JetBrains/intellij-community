public class YoYo {
    Object y;
    class <caret>YoYoYo {
        Object x = y;
        Object[] xx = {y}; 
        void foo (){
            YoYo yoYoy = YoYo.this;
            Object t = y;
            Object t1 = yoYoy.y;
        }
    }
}

