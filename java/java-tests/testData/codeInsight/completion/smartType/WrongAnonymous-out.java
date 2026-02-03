import java.io.File;
import java.io.FilenameFilter;

class Intermediate {

    {
        new File(".").listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                <selection>return false;</selection>
            }
        })
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                    }
                });


    }

}
