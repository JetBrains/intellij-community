public class YoYo {
    class <caret>YoYoYo {
        void foo (){
            YoYo yoYoy = YoYo.this;
        }
    }

    class Other {
        {
            new YoYoYo();
        }
    }
}
