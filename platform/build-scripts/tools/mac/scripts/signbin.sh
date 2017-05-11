#!/bin/bash

#immediately exit script with an error if a command fails
set -euo pipefail

FILENAME=$1
USERNAME=$2
PASSWORD=$3
CODESIGN_STRING=$4
FILEPATH=$(dirname $0)/$FILENAME

# Make sure *.p12 is imported into local KeyChain
security unlock-keychain -p ${PASSWORD} /Users/${USERNAME}/Library/Keychains/login.keychain

attemp=1
limit=3
set +e
while [ $attemp -le $limit ]
do
  echo "signing (attemp $attemp) ${FILEPATH}"
  codesign -v --deep --force -s "${CODESIGN_STRING}" ${FILEPATH}
  if [ "$?" != "0" ]; then
    let "attemp += 1"
    if [ $attemp -eq $limit ]; then
      set -e
    fi
    echo "wait for 30 sec and try to sign again"
    sleep 30;
  else
    let "attemp += $limit"
    echo "signing done"
    codesign -v ${FILEPATH} -vvvvv
    echo "check sign done"
    fi
done
