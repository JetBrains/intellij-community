Gant -- A Groovy way of scripting Ant tasks.

This is Gant, a Groovy way of working with Ant tasks -- no more XML :-)

The method of installation depends on whether you have downloaded a tarball or
zipfile distribution, or you have a Bazaar branch or a Subversion store
checkout (or even a Git clone of the Subversion repository).

Distribution
------------

The Gant distributions contain a ready-made install directory hierarchy.
Untar the tarball or unzip the zipfile to the location where you want the Gant
installation to reside.  A directory with the name structured
gant-<gant-version-number> will be created in the location specified for the
untar or unzip.

There are a number of distinct distributions:

          1.  Requires a separate Groovy installation.  There are builds:
                a.  compiled against Groovy 1.5.8;
                b.  compiled against Groovy 1.6.3; and
                c.  compiled against Groovy Trunk (1.7-beta-1-SNAPSHOT).

            2.  Self-contained, includes all dependent jars.

You might like to set up an environment variable GANT_HOME set to the
directory created by the untar or unzip, though this is not essential, it is
just an efficiency.

The script $GANT_HOME/bin/gant for systems with a Posix shell, or
$GANT_HOME/bin/gant.bat on Windows is the mechanism for launching a Gant run.

Using a Bazaar Branch or a Checkout of the Subversion Repository
----------------------------------------------------------------

You first need to get a source tree.  If you want to use a Bazaar branch as
your source (Bazaar is the VCS used for the master source of Gant) then:

    bzr branch lp:gant

this is a mirror of the master Bazaar branch, as indeed is the Subversion
repository at Codehaus).  The alternative is to get the Subversion repository
using one of the following:

   bzr branch http://svn.codehaus.org/gant/gant/trunk Gant_Trunk
   svn co http://svn.codehaus.org/gant/gant/trunk Gant_Trunk
   git clone http://svn.codehaus.org/gant/gant/trunk Gant_Trunk

Once you have a Gant source tree, you will need to create a file called
local.build.properties at the top level of that tree containing a definition
of the property installDirectory.  This property defines the directory of the
installation (NB not the parent).  An example definition:

  installDirectory = ${user.home}/lib/JavaPackages/gant-${gantVersion}

Having created local.build.properties with the installDirectory property
definition, to install Gant for the first time, you need to either:

-- install Gant from a distribution as above and then type "gant install";
    or

-- type "ant install" -- assuming you have Ant installed.

To install a new build of Gant where one is installed already, you can simply
type "gant install".

Contact
-------

If you have any problems using Gant, or have any ideas for improvements,
please make use of the Gant users mailing list: user@gant.codehaus.org

Russel Winder <russel.winder@concertant.com>

;;; Local Variables: ***
;;; fill-column: 78 ***
;;; End: ***
