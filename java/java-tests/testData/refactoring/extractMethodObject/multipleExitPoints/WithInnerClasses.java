public class Demo {


    public static void main(String[] args) {

        <selection>Pojo1 p1 = new Pojo1("p1");
        Pojo2 p2 = new Pojo2("p2"){
            @Override
            public String getContent() {
                return "------------";
            }
        };
        </selection>

        System.out.println(p1.getContent() + p2.getContent());

    }


    public static class Pojo1 {
        private String content;

        public Pojo1(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    public static class Pojo2 {
        private String content;

        public Pojo2(String content) {
            this.content = content;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}



