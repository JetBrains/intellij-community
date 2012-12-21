package org.hanuna.gitalk.controller;

import org.hanuna.gitalk.commitmodel.Commit;
import org.hanuna.gitalk.common.Timer;
import org.hanuna.gitalk.parser.GitLogParser;
import org.hanuna.gitalk.refs.Ref;
import org.hanuna.gitalk.refs.RefParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

/**
 * @author erokhins
 */
public class GitLogReader {

    // modifiable List
    public static List<Commit> readAllCommits() throws IOException  {
        Process p = Runtime.getRuntime().exec("git log --all --date-order --format=%h|-%p|-%an|-%ct|-%s");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        Timer t = new Timer("git run + commits model");
        GitLogParser parser = new GitLogParser(r);
        List<Commit> commits = parser.readAllCommits();
        t.print();
        return commits;
    }

    // modifiable List
    public static List<Commit> readLastCommits(int monthCount) throws IOException  {
        assert monthCount > 0;
        Process p = Runtime.getRuntime().exec("git log --all --since=\"" + monthCount
                + " month ago\" --date-order --format=%h|-%p|-%an|-%ct|-%s");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        Timer t = new Timer("git run + commits model");
        GitLogParser parser = new GitLogParser(r);
        List<Commit> commits = parser.readAllCommits();
        t.print();
        return commits;
    }

    // modifiable List
    public static List<Commit> readLastCommits() throws IOException  {
        return readLastCommits(6);
    }

    // modifiable List
    public static List<Ref> readAllRefs() throws IOException {
        Process p = Runtime.getRuntime().exec("git show-ref --head --abbrev");
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));

        Timer t = new Timer("git run + commits model");
        List<Ref> refs = RefParser.allRefs(r);
        t.print();
        return refs;
    }

}
