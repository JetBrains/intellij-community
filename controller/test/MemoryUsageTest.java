import org.hanuna.gitalk.commitmodel.builder.CommitLogData;
import org.hanuna.gitalk.common.MyTimer;
import org.hanuna.gitalk.controller.git_log.AbstractProcessOutputReader;
import org.hanuna.gitalk.controller.git_log.GitException;

import java.io.IOException;
import java.util.List;

/**
 * @author erokhins
 */
public class MemoryUsageTest {
    public static void main(String[] args) throws IOException, GitException, InterruptedException {
        final MyTimer gitThink = new MyTimer("git think");
        final MyTimer commitReadTimer = new MyTimer("commits read");
        MyReader commitReader = new MyReader(new AbstractProcessOutputReader.ProgressUpdater() {
            @Override
            public void updateFinishedCount(int count) {
                if (count == 0) {
                    gitThink.print();
                    commitReadTimer.clear();
                }
                if (count % 100 == 0) {
                    System.out.println(count);
                }
            }
        });
        List<CommitLogData> commitLogData = commitReader.readAllLogData();
        commitReadTimer.print();
        while (true) {
            int c = 1;
            for (int i = 0; i < 1000; i++) {
                c = i;
                for (int j = 1; j < 100000; j++) {
                    c = 4 * c % j;
                }
            }
            System.out.print(c);
            System.out.println(commitLogData.get(0).getCommitMessage());
        }
    }
}
