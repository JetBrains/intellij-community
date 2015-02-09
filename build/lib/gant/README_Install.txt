Gant -- A Groovy way of scripting Ant tasks.


This is Gant, a Groovy way of working with Ant tasks -- no more XML :-)

The method of installation depends on whether you have downloaded a tarball or
zipfile distribution, or you have a Git clone -- or even a Bazaar branch -- of
the source.


Distribution
------------

The Gant distributions contain a ready-made install directory hierarchy.
Untar the tarball or unzip the zipfile to the location where you want the Gant
installation to reside. A directory with the name structured
gant-<gant-version-number> will be created in the location specified for the
untar or unzip.

There are a number of distinct distributions:

          1. Requires a separate Groovy installation. There are builds:
                a. compiled against Groovy 1.7.10; and
                b. compiled against Groovy 1.8.6; and
                c. compiled against Groovy 2.0.0-beta-2

          2. Self-contained, includes all dependent jars.

You might like to set up an environment variable GANT_HOME set to the
directory created by the untar or unzip, though this is not essential, it is
just an efficiency.

The script $GANT_HOME/bin/gant for systems with a Posix shell, or
$GANT_HOME/bin/gant.bat on Windows is the mechanism for launching a Gant run.

Distributions 1a, 1b and 1c only include the direct Gant materials. The Maven
target set depends on use of the Maven Ant tasks, and the Ivy tool depends on
the Ivy jar, these will have to be downloaded and installed into
$GANT_HOME/lib unless they are already available on on your CLASSPATH.


Using a Git Clone
-----------------

Gant's mainline is a Git repository on GitHub, see

       https://github.com/Gant/Gant

you should fork this on GitHub and then clone to give you a local repository.

The repository on Codehaus at:

       git://git.codehaus.org/gant.git

is an administrative clone of the GitHub mainline and should not be used in
normal circumstances.

Gradle is used as the build system for Gant, so you will need to set the
gant_installPath property in ~/.gradle/gradle.properties so you can install
Gant. So for example:

       gant_installPath = ${System.properties.'user.home'}/lib/JavaPackages/gant-trunk

Then you type:

     ./gradlew :gant:install

and all the necessary magic happens. The first time you use the Gradle
Wrapper, it will connect to the Internet to download the various jars that
comprise Gradle. This takes a while. However this is only needed the first
time, thereafter it uses the version you downloaded.

You probably want to set the GROOVY_HOME environment variable to point at the
Groovy installation that the Gant installation is to work with.


Using a Bazaar Branch
---------------------

For anyone prefering to use Bazaar rather than Git, there is an automated
bridge of the master branch of the Git clone on Launchpad.

To get a branch:

        bzr branch lp:gant Gant

or if you want to use bzr-git directly:

        bzr branch git://github.com/Gant/Gant.git Gant

(If you are going to actively develop Gant, you almost certainly want to have
a shared repository in which this mirror branch is kept so that you can then
make feature branches from it.)

All the information in the previous section about Gradle and building Gant
apply when using Bazaar.


Contact
-------

If you have any problems using Gant, or have any ideas for improvements,
please make use of the Gant users mailing list: user@gant.codehaus.org

Russel Winder <russel@winder.org.uk>


;;; Local Variables: ***
;;; fill-column: 78 ***
;;; End: ***
