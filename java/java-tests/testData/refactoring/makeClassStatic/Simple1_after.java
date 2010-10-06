public class YoYo {
    int y;
    static class YoYoYo {
        private YoYo anObject;

        public YoYoYo(YoYo anObject) {
            this.anObject = anObject;
        }

        void foo (){
            YoYo yoYoy = anObject;
            int t = anObject.y;
            int t1 = yoYoy.y;
            anObject.new Other();
        }
    }

    class Other {}
}

