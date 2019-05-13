class GoodCodeIsRed {

    public void test() {
        FileIF file = new FileImpl();
        file.getInputStream();
    }
}

class FileImpl implements FileIF {

    public void getInputStream() {
    }

}

interface FileIF extends BasicFileIF, DataSource {
}

interface BasicFileIF {
    void getInputStream();
}


interface DataSource {
    void getInputStream() throws java.io.IOException;
}
