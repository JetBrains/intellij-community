public class YoYo {
    int y;
    class <caret>YoYoYo {
        void foo (){
            YoYo yoYoy = YoYo.this;
            int t = y;
            int t1 = yoYoy.y;
            new Other();
        }
    }

    class Other {}
}

