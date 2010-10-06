public class YoYo {
    Object y;
    static class YoYoYo {
        Object x;
        Object[] xx;
        private YoYo anObject;
        private Object y;

        public YoYoYo(YoYo anObject, Object y) {
            this.anObject = anObject;
            this.y = y;
            this.x = y;
            this.xx = new Object[]{y};
        }

        void foo (){
            YoYo yoYoy = anObject;
            Object t = y;
            Object t1 = yoYoy.y;
        }
    }
}

