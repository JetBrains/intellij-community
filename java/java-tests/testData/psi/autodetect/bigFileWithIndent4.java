/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

public class Percolation {
    private final WeightedQuickUnionUF smartField;
    private final int N;
    private boolean[][] isOpened;
    private boolean[] connectedToBottom;
    private boolean myPercolates = false;

    public Percolation(int N) {
        if (N <= 0) {
            throw new IllegalArgumentException();
        }

        this.N = N;
        isOpened = new boolean[N + 2][N + 2];

        for (int i = 0; i < N + 2; i++) {
            for (int j = 0; j < N + 2; j++) {
                isOpened[i][j] = false;
            }
        }

        smartField = new WeightedQuickUnionUF(N * N + 2);
        for (int i = 1; i <= N; i++) {
            smartField.union(0, i);
        }


        connectedToBottom = new boolean[N * N + 1];
        for (int i = N * (N - 1) + 1; i <= N * N; i++) {
            connectedToBottom[smartField.find(i)] = true;
        }
    }

    public void open(int i, int j) {
        assertBounds(i, j);

        isOpened[i][j] = true;

        int smartCoord = getPlainCoordinates(i, j);

        if (isOpened[i - 1][j]) unionGroups(smartCoord, smartCoord - N);
        if (isOpened[i + 1][j]) unionGroups(smartCoord, smartCoord + N);
        if (isOpened[i][j - 1]) unionGroups(smartCoord, smartCoord - 1);
        if (isOpened[i][j + 1]) unionGroups(smartCoord, smartCoord + 1);

        if (connectedToBottom(smartField.find(smartCoord)) && isFull(i, j)) {
            myPercolates = true;
        }
    }

    private void unionGroups(int first, int second) {
        int firstGroupId = smartField.find(first);
        int secondGroupId = smartField.find(second);

        if (connectedToBottom(firstGroupId)) {
            setConnectedToBottom(secondGroupId);
        }
        else if (connectedToBottom(secondGroupId)) {
            setConnectedToBottom(firstGroupId);
        }

        smartField.union(first, second);
    }

    private void setConnectedToBottom(int secondGroupId) {
        connectedToBottom[secondGroupId] = true;
    }

    private boolean connectedToBottom(int firstGroupId) {
        return connectedToBottom[firstGroupId];
    }

    public boolean isOpen(int i, int j) {
        assertBounds(i, j);
        return isOpened[i][j];
    }

    public boolean isFull(int i, int j) {
        assertBounds(i, j);
        int coord = getPlainCoordinates(i, j);
        return isOpen(i, j) && smartField.connected(0, coord);
    }

    private int getPlainCoordinates(int i, int j) {
        return (i - 1) * N + j;
    }

    public boolean percolates() {
        return myPercolates;
    }

    private void assertBounds(int i, int j) {
        boolean inBounds = (i > 0 && i <= N) && (j > 0 && j <= N);
        if (!inBounds) {
            throw new IndexOutOfBoundsException("i: " + i + " j: " + j + " max N: " + N);
        }
    }
}