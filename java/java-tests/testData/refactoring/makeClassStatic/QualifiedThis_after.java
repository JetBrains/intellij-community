public class YoYo {
    static class YoYoYo {
        private final YoYo anObject;

        public YoYoYo(YoYo anObject) {
            this.anObject = anObject;
        }

        void foo (){
            YoYo yoYoy = anObject;
        }
    }

    class Other {
        {
            new YoYoYo(YoYo.this);
        }
    }
}
