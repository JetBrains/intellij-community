import java.io.File;
import java.io.FilenameFilter;

class Intermediate {

    {
        new File(".").listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                <selection>return false;  //To change body of implemented methods use File | Settings | File Templates.</selection>
            }
        })
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                    }
                });


    }

}
