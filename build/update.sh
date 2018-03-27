#!/bin/sh
#
# This script updates your IntelliJ IDEA CE installation from the latest compiled classes. This way you can easily
# upgrade your working IDEA to the latest changes.
#
# Before you run the script, ensure you have the following:
# 1. Your project for IntelliJ IDEA CE is fully built (do 'Rebuild Project' if you're not sure)
# 2. WORK_IDEA_HOME points to the directory of IntelliJ IDEA build you want to upgrade
# 3. DEV_IDEA_HOME points to the directory of the project you built at step 1
# 4. You quit IntelliJ IDEA

if [ ! -f "$WORK_IDEA_HOME/bin/inspect.sh" -a -f "$WORK_IDEA_HOME/Contents/bin/inspect.sh" ]; then
  WORK_IDEA_HOME="$WORK_IDEA_HOME/Contents"
fi
if [ ! -f "$WORK_IDEA_HOME/bin/inspect.sh" ]; then
  echo "WORK_IDEA_HOME must be defined and point to the installation you're updating."
  exit 1
fi

if [ ! -f "$DEV_IDEA_HOME/build/update.sh" ]; then
  echo "DEV_IDEA_HOME must be defined and point to the source base you're updating from."
  exit 1
fi

JAVA_BIN="java"
if [ -n "$JAVA_HOME" ]; then
  JAVA_BIN="$JAVA_HOME/bin/java"
  if [ ! -x "$JAVA_BIN" ]; then
    echo "JAVA_HOME must point to a valid Java installation"
    exit 1
  fi
fi

echo "Updating $WORK_IDEA_HOME from compiled classes in $DEV_IDEA_HOME"

ANT_HOME="$DEV_IDEA_HOME/lib/ant"
"$JAVA_BIN" -Xms64m -Xmx512m -Dant.home="$ANT_HOME" -classpath "$ANT_HOME/lib/ant-launcher.jar" org.apache.tools.ant.launch.Launcher \
 -f "$DEV_IDEA_HOME/build/update.xml" -Dwork.idea.home="$WORK_IDEA_HOME" ${TARGET}

if [ "$?" != "0" ]; then
  echo "Update failed; work IDEA build not modified."
  rm -rf "$WORK_IDEA_HOME/___tmp___"
  exit 2
fi

rm -rf "$WORK_IDEA_HOME/lib"
rm -rf "$WORK_IDEA_HOME/plugins"

cp -R "$DEV_IDEA_HOME/out/deploy/"* "$WORK_IDEA_HOME"

OS_TYPE=`uname -s`
if [ "$OS_TYPE" = "Linux" ]; then
  cp -a "$DEV_IDEA_HOME/bin/linux/"fsnotifier* "$WORK_IDEA_HOME/bin"
  cp -a "$DEV_IDEA_HOME/bin/linux/"*.py "$WORK_IDEA_HOME/bin"
elif [ "$OS_TYPE" = "Darwin" ]; then
  cp -a "$DEV_IDEA_HOME/bin/mac/"*.dylib "$WORK_IDEA_HOME/bin"
  cp -a "$DEV_IDEA_HOME/bin/mac/fsnotifier" "$WORK_IDEA_HOME/bin"
  cp -a "$DEV_IDEA_HOME/bin/mac/restarter" "$WORK_IDEA_HOME/bin"
fi
