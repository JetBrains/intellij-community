Gant -- A Groovy way of scripting Ant tasks.

This is Gant, a Groovy way of working with Ant tasks -- no more XML :-)

The method of installation depends on whether you have downloaded a tarball or
zipfile distribution, or you have a Bazaar branch -- or a Subversion checkout,
or even a Git clone -- of the source.

Distribution
------------

The Gant distributions contain a ready-made install directory hierarchy.
Untar the tarball or unzip the zipfile to the location where you want the Gant
installation to reside.  A directory with the name structured
gant-<gant-version-number> will be created in the location specified for the
untar or unzip.

There are a number of distinct distributions:

          1.  Requires a separate Groovy installation.  There are builds:
                a.  compiled against Groovy 1.6.7; and
                b.  compiled against Groovy 1.7-rc-2.

          2.  Self-contained, includes all dependent jars.

You might like to set up an environment variable GANT_HOME set to the
directory created by the untar or unzip, though this is not essential, it is
just an efficiency.

The script $GANT_HOME/bin/gant for systems with a Posix shell, or
$GANT_HOME/bin/gant.bat on Windows is the mechanism for launching a Gant run.

Distributions 1a and 1b only include the direct Gant materials.  The Maven
target set depends on use of the Maven Ant tasks, and the Ivy tool depends on
the Ivy jar, these will have to be downloaded and installed into
$GANT_HOME/lib unless they are already available on on your CLASSPATH.

Using a Bazaar Branch
---------------------

You first need to get a source tree.  Bazaar is the version control system
used for developing Gant.  For reasons that are unlikely to become apparent
quickly, the master branch is actually held in the Subversion repository at
Codehaus.  However the effective mainline is a branch on Launchpad.  So to get
a branch:

        bzr branch lp:gant Gant_Trunk

or

        bzr branch http://svn.codehaus.org/gant/gant/trunk Gant_Trunk

(If you are going to actively develop Gant, you almost certainly want to have
a shared repository in which this mirror branch is kept so that you can then
make feature branches from it.)

Gradle is used as the build system for Gant, so you will need to set the
gant_installPath property in ~/.gradle/gradle.properties so you can install
Gant.  So for example:

       gant_installPath = ${System.properties.'user.home'}/lib/JavaPackages/gant-trunk

Then you type:

     ./gradlew :gant:install

and all the necessary magic happens.  The first time you use the Gradle
Wrapper, it will connect to the Internet to download the various jars that
comprise Gradle.  This takes a while.  However this is only needed the first
time, thereafter it uses the version you downloaded.


You probably want to set the GROOVY_HOME environment variable to point at the
Groovy installation that the Gant installation is to work with.

Contact
-------

If you have any problems using Gant, or have any ideas for improvements,
please make use of the Gant users mailing list: user@gant.codehaus.org

Russel Winder <russel.winder@concertant.com>

;;; Local Variables: ***
;;; fill-column: 78 ***
;;; End: ***
